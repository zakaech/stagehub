package com.stagehub.analyse.service;

import com.stagehub.analyse.event.AnalyseCandidature;
import com.stagehub.analyse.repository.EvenementTraiteRepository;
import com.stagehub.candidature.entity.AnalyseIa;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutAnalyse;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enregistre l'état de l'analyse reçu d'ai-service dans la candidature, une seule fois par événement
 * (consommateur idempotent : l'id est inscrit dans la même transaction). Rien ici ne touche au statut de la
 * candidature : le score est une aide, jamais une décision.
 * <ul>
 *   <li>candidature introuvable (supprimée depuis) : l'état est ignoré, sans erreur ni nouvelle tentative ;</li>
 *   <li>état plus ancien que celui déjà enregistré : ignoré (Kafka garde l'ordre par candidature, mais un
 *       événement rejoué ne doit pas faire revenir l'affichage en arrière).</li>
 * </ul>
 */
@Service
public class ResultatAnalyseService {

    private static final Logger log = LoggerFactory.getLogger(ResultatAnalyseService.class);

    private final EvenementTraiteRepository evenementsTraites;
    private final CandidatureRepository candidatures;
    private final Clock clock;

    public ResultatAnalyseService(EvenementTraiteRepository evenementsTraites, CandidatureRepository candidatures,
                                  Clock clock) {
        this.evenementsTraites = evenementsTraites;
        this.candidatures = candidatures;
        this.clock = clock;
    }

    @Transactional
    public void appliquer(UUID evenementId, AnalyseCandidature evenement) {
        if (evenementsTraites.inscrire(evenementId, "AnalyseCandidature", clock.instant()) == 0) {
            log.info("Événement {} (AnalyseCandidature) déjà traité : doublon ignoré", evenementId);
            return;
        }
        Optional<Candidature> trouvee = candidatures.findById(evenement.candidatureId());
        if (trouvee.isEmpty()) {
            log.info("Analyse de la candidature {} ({}) ignorée : candidature introuvable (supprimée depuis ?)",
                    evenement.candidatureId(), evenement.statut());
            return;
        }
        AnalyseIa analyse = trouvee.get().getAnalyse();
        if (analyse.getDate() != null && evenement.date() != null && evenement.date().isBefore(analyse.getDate())) {
            log.info("Analyse de la candidature {} : état {} plus ancien que l'état enregistré, ignoré",
                    evenement.candidatureId(), evenement.statut());
            return;
        }
        StatutAnalyse statut = StatutAnalyse.valueOf(evenement.statut());
        analyse.setStatut(statut);
        analyse.setDate(evenement.date());
        analyse.setMotifEchec(statut == StatutAnalyse.ECHEC ? evenement.motifEchec() : null);
        analyse.setProchaineTentative(statut == StatutAnalyse.EN_ATTENTE ? evenement.prochaineTentative() : null);
        if (statut == StatutAnalyse.TERMINEE) {
            analyse.setScore(evenement.score());
            analyse.setJustification(evenement.justification());
            analyse.setResume(copie(evenement.resume()));
            analyse.setTechnologiesTrouvees(copie(evenement.technologiesTrouvees()));
            analyse.setTechnologiesOffre(evenement.technologiesOffre());
            analyse.setAlerteEcart(Boolean.TRUE.equals(evenement.alerteEcart()));
            analyse.setContenuSuspect(Boolean.TRUE.equals(evenement.contenuSuspect()));
            analyse.setModele(evenement.modele());
            log.info("Candidature {} : analyse terminée, score indicatif {} ({} technologies sur {})",
                    evenement.candidatureId(), evenement.score(), analyse.getTechnologiesTrouvees().size(),
                    evenement.technologiesOffre());
        } else {
            log.info("Candidature {} : analyse {}{}", evenement.candidatureId(), statut,
                    statut == StatutAnalyse.ECHEC ? " (" + evenement.motifEchec() + ")" : "");
        }
    }

    private static List<String> copie(List<String> valeurs) {
        return valeurs == null ? List.of() : List.copyOf(valeurs);
    }
}
