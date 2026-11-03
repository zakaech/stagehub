package com.stagehub.ai.service;

import com.stagehub.ai.analyse.ComptageTechnologies;
import com.stagehub.ai.analyse.ComptageTechnologies.Correspondance;
import com.stagehub.ai.analyse.DetectionContenuSuspect;
import com.stagehub.ai.analyse.ModeleAnalyse;
import com.stagehub.ai.analyse.OffreClient;
import com.stagehub.ai.analyse.OffreClient.OffreAAnalyser;
import com.stagehub.ai.config.AnalyseProperties;
import com.stagehub.ai.cv.CvAbsentException;
import com.stagehub.ai.cv.CvIllisibleException;
import com.stagehub.ai.cv.LectureCv;
import com.stagehub.ai.cv.TexteCv;
import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.entity.MotifEchec;
import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.event.AnalyseCandidature;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import com.stagehub.outbox.service.OutboxService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Analyse des candidatures, appelée par {@link TacheAnalyse} : une demande à la fois.
 * <ol>
 *   <li><b>réservation</b>, transaction courte : la demande passe EN_COURS (verrou {@code SKIP LOCKED}) ;</li>
 *   <li><b>analyse hors transaction</b> (plusieurs minutes sur processeur) : CV, offre, comptage des technologies,
 *       détection de consignes, modèle. Aucune connexion à la base ni verrou n'est tenu pendant ce temps ;</li>
 *   <li><b>résultat</b>, transaction courte : la demande passe TERMINEE (ou ECHEC) et l'événement est écrit dans
 *       l'outbox, ensemble ou pas du tout.</li>
 * </ol>
 * Chaque changement d'état (EN_COURS, EN_ATTENTE après une panne, TERMINEE, ECHEC) est publié dans
 * {@code stagehub.candidature.analyse} : candidature-service l'affiche à l'entreprise.
 * Panne passagère (modèle, réseau, réponse invalide) : nouvelle tentative plus tard, jusqu'au maximum, puis ECHEC.
 * CV absent ou illisible, offre disparue : ECHEC tout de suite (réessayer ne changerait rien).
 * Les étapes 1 et 3 passent par un {@link TransactionTemplate} et non par {@code @Transactional} : appelées depuis
 * cette même classe, des méthodes annotées ne passeraient pas par le proxy (piège de l'auto-invocation).
 */
@Service
public class AnalyseCandidatureService {

    static final String TOPIC = "stagehub.candidature.analyse";
    private static final Logger log = LoggerFactory.getLogger(AnalyseCandidatureService.class);

    private final DemandeAnalyseRepository demandes;
    private final LectureCv lectureCv;
    private final OffreClient offreClient;
    private final ComptageTechnologies comptage;
    private final DetectionContenuSuspect detection;
    private final ModeleAnalyse modele;
    private final OutboxService outbox;
    private final TransactionTemplate transaction;
    private final RepriseDeTrace repriseDeTrace;
    private final AnalyseProperties proprietes;
    private final Clock clock;

    public AnalyseCandidatureService(DemandeAnalyseRepository demandes, LectureCv lectureCv, OffreClient offreClient,
                                     ComptageTechnologies comptage, DetectionContenuSuspect detection,
                                     ModeleAnalyse modele, OutboxService outbox, TransactionTemplate transaction,
                                     RepriseDeTrace repriseDeTrace, AnalyseProperties proprietes, Clock clock) {
        this.demandes = demandes;
        this.lectureCv = lectureCv;
        this.offreClient = offreClient;
        this.comptage = comptage;
        this.detection = detection;
        this.modele = modele;
        this.outbox = outbox;
        this.transaction = transaction;
        this.repriseDeTrace = repriseDeTrace;
        this.proprietes = proprietes;
        this.clock = clock;
    }

    /** Analyse la prochaine demande prête, s'il y en a une. Vrai si une demande a été traitée. */
    public boolean traiterLaProchaine() {
        Optional<DemandeAnalyse> reservee = transaction.execute(statut -> reserver());
        if (reservee == null || reservee.isEmpty()) {
            return false;
        }
        DemandeAnalyse demande = reservee.get();
        // Les logs de l'analyse et l'événement publié gardent le traceId de la candidature d'origine
        repriseDeTrace.executer(demande.getTraceparent(), "analyse candidature", () -> analyser(demande));
        return true;
    }

    private Optional<DemandeAnalyse> reserver() {
        Instant maintenant = clock.instant();
        return demandes.verrouillerProchaine(maintenant, maintenant.minus(proprietes.bail())).map(demande -> {
            demande.setStatut(StatutAnalyse.EN_COURS);
            demande.setDebutAnalyse(maintenant);
            demande.setTentatives(demande.getTentatives() + 1);
            return demandes.save(demande);
        });
    }

    private void analyser(DemandeAnalyse demande) {
        Long candidatureId = demande.getCandidatureId();
        long debut = System.nanoTime();
        log.info("Analyse de la candidature {} (tentative {} sur {})", candidatureId, demande.getTentatives(),
                proprietes.tentativesMax());
        // L'entreprise voit « analyse en cours » (publié dans la trace de la candidature)
        transaction.executeWithoutResult(etat -> publier(AnalyseCandidature.etat(candidatureId,
                StatutAnalyse.EN_COURS.name(), null, null, clock.instant())));
        try {
            if (demande.getCvFichier() == null) {
                throw new CvAbsentException(candidatureId);
            }
            TexteCv cv = lectureCv.texteDuCv(candidatureId);
            OffreAAnalyser offre = lireOffre(demande.getOffreId());
            List<String> regles = detection.reglesDeclenchees(cv.texte());
            if (!regles.isEmpty()) {
                log.warn("Candidature {} : contenu inhabituel dans le CV ({})", candidatureId, String.join(", ", regles));
            }
            Correspondance technologies = comptage.compter(offre.technologies(), cv.texte());
            ModeleAnalyse.Resultat resultat = modele.analyser(offre, cv.texte());
            int score = resultat.reponse().score();
            Integer pourcentage = technologies.pourcentage();
            boolean alerte = pourcentage != null && Math.abs(score - pourcentage) >= proprietes.ecartAlerte();
            long dureeMs = (System.nanoTime() - debut) / 1_000_000;
            log.info("Candidature {} analysée par {} en {} s : score {}, technologies {} sur {}{} ({} jetons lus, {} produits)",
                    candidatureId, resultat.modele(), dureeMs / 1000, score, technologies.trouvees().size(),
                    technologies.total(), alerte ? ", écart flagrant avec le score" : "", resultat.jetonsLus(),
                    resultat.jetonsProduits());
            terminer(demande, StatutAnalyse.TERMINEE, null, resultat.modele(), dureeMs, new AnalyseCandidature(
                    candidatureId, StatutAnalyse.TERMINEE.name(), null, null, score, resultat.reponse().justification(),
                    resultat.reponse().resume(), technologies.trouvees(), technologies.total(), alerte,
                    !regles.isEmpty(), resultat.modele(), clock.instant()));
        } catch (CvAbsentException e) {
            echouer(demande, MotifEchec.CV_ABSENT, "candidature sans CV", debut);
        } catch (CvIllisibleException e) {
            echouer(demande, MotifEchec.CV_ILLISIBLE, e.getMessage(), debut);
        } catch (OffreIntrouvableException e) {
            echouer(demande, MotifEchec.OFFRE_INTROUVABLE, "offre " + demande.getOffreId() + " introuvable", debut);
        } catch (RuntimeException e) {
            reessayerOuEchouer(demande, e, debut);
        }
    }

    private OffreAAnalyser lireOffre(Long offreId) {
        try {
            return offreClient.offre(offreId);
        } catch (FeignException.NotFound e) {
            throw new OffreIntrouvableException();
        }
    }

    private void reessayerOuEchouer(DemandeAnalyse demande, RuntimeException cause, long debut) {
        if (demande.getTentatives() >= proprietes.tentativesMax()) {
            log.error("Candidature {} : analyse abandonnée après {} tentatives ({})", demande.getCandidatureId(),
                    demande.getTentatives(), cause.getMessage(), cause);
            echouer(demande, MotifEchec.ANALYSE_IMPOSSIBLE, cause.getMessage(), debut);
            return;
        }
        Instant prochaine = clock.instant().plus(proprietes.attenteApres(demande.getTentatives()));
        log.warn("Candidature {} : analyse impossible pour l'instant ({}), nouvelle tentative à {}",
                demande.getCandidatureId(), cause.getMessage(), prochaine);
        transaction.executeWithoutResult(statut -> demandes.findById(demande.getCandidatureId()).ifPresent(d -> {
            d.setStatut(StatutAnalyse.EN_ATTENTE);
            d.setProchaineTentative(prochaine);
            d.setDebutAnalyse(null);
            // L'entreprise voit « en attente, nouvelle tentative à ... »
            publier(AnalyseCandidature.etat(d.getCandidatureId(), StatutAnalyse.EN_ATTENTE.name(), null, prochaine,
                    clock.instant()));
        }));
    }

    private void echouer(DemandeAnalyse demande, MotifEchec motif, String detail, long debut) {
        log.warn("Candidature {} : analyse impossible, définitivement ({} : {})", demande.getCandidatureId(), motif, detail);
        terminer(demande, StatutAnalyse.ECHEC, motif, null, (System.nanoTime() - debut) / 1_000_000,
                AnalyseCandidature.etat(demande.getCandidatureId(), StatutAnalyse.ECHEC.name(), motif.name(), null,
                        clock.instant()));
    }

    /** Statut final et événement, dans la même transaction (outbox) : les deux sont enregistrés, ou aucun. */
    private void terminer(DemandeAnalyse demande, StatutAnalyse statut, MotifEchec motif, String nomModele, long dureeMs,
                          AnalyseCandidature evenement) {
        transaction.executeWithoutResult(etat -> {
            DemandeAnalyse d = demandes.findById(demande.getCandidatureId()).orElseThrow();
            d.setStatut(statut);
            d.setMotifEchec(motif);
            d.setModele(nomModele);
            d.setDureeMs(dureeMs);
            d.setDateFin(clock.instant());
            d.setDebutAnalyse(null);
            d.setProchaineTentative(null);
            publier(evenement);
        });
    }

    /** Dans l'outbox, donc dans la transaction en cours (MANDATORY) : publié si, et seulement si, elle est validée. */
    private void publier(AnalyseCandidature evenement) {
        outbox.enregistrer(TOPIC, evenement.candidatureId().toString(), evenement);
    }

    /** L'offre a disparu d'offre-service (404) : définitif. */
    static class OffreIntrouvableException extends RuntimeException {
    }
}
