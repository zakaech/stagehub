package com.stagehub.candidature.service;

import com.stagehub.audit.aspect.Audite;
import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.client.OffreClient;
import com.stagehub.candidature.client.OffreInterne;
import com.stagehub.candidature.dto.CandidatureRequest;
import com.stagehub.candidature.dto.OffreResumeResponse;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.event.CandidatureStatutChangeEvent;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.mapper.CandidatureMapper;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.commons.dto.PageResponse;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.OffreNonDisponibleException;
import com.stagehub.common.Topics;
import com.stagehub.outbox.service.OutboxService;
import com.stagehub.etudiant.entity.Etudiant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CandidatureService {

    private final CandidatureRepository candidatureRepository;
    private final EnregistrementCandidature enregistrement;
    private final OffreClient offreClient;
    private final CandidatureMapper candidatureMapper;
    private final OutboxService outbox;
    /** L'analyse par l'IA tourne-t-elle ? Sinon, une analyse en attente s'affiche « désactivée » (phase 8 e). */
    private final boolean iaActive;

    public CandidatureService(CandidatureRepository candidatureRepository, EnregistrementCandidature enregistrement,
                              OffreClient offreClient, CandidatureMapper candidatureMapper, OutboxService outbox,
                              @Value("${stagehub.analyse.active:false}") boolean iaActive) {
        this.candidatureRepository = candidatureRepository;
        this.enregistrement = enregistrement;
        this.offreClient = offreClient;
        this.candidatureMapper = candidatureMapper;
        this.outbox = outbox;
        this.iaActive = iaActive;
    }

    /**
     * Spec §4.3 : offre OUVERTE et date limite non dépassée (règle évaluée par offre-service, données à jour) ; un
     * doublon renvoie 409. offre-service injoignable : 503, on ne prend jamais une candidature non vérifiée.
     * NOT_SUPPORTED : l'appel réseau se fait hors transaction ; l'écriture a sa propre transaction, courte.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CandidatureEtudiantResponse postuler(Long etudiantId, Long offreId, CandidatureRequest request) {
        OffreInterne offre = offreClient.offre(offreId);
        if (!offre.accepteCandidatures()) {
            throw new OffreNonDisponibleException(
                    "Cette offre n'accepte plus de candidatures (fermée ou date limite dépassée)");
        }
        Candidature candidature = enregistrement.enregistrer(etudiantId, offre, request);
        return candidatureMapper.toEtudiantResponse(candidature,
                new OffreResumeResponse(offre.id(), offre.titre(), offre.entrepriseNom(), offre.ville(), offre.statut()));
    }

    /**
     * Le résumé de chaque offre (statut à jour, titre modifié...) est lu dans offre-service, en un seul appel pour la
     * page. Si offre-service ne répond pas, la page s'affiche quand même, avec l'instantané pris au moment de postuler.
     */
    public PageResponse<CandidatureEtudiantResponse> listerCandidaturesEtudiant(Long etudiantId, Pageable pageable) {
        Page<Candidature> page = candidatureRepository.findByEtudiantId(etudiantId, pageable);
        Set<Long> offreIds = page.getContent().stream().map(Candidature::getOffreId).collect(Collectors.toSet());
        Map<Long, OffreResumeResponse> resumes = offreIds.isEmpty() ? Map.of() : offreClient.resumes(offreIds).stream()
                .collect(Collectors.toMap(OffreResumeResponse::id, Function.identity()));
        return PageResponse.of(page, c -> candidatureMapper.toEtudiantResponse(c,
                resumes.getOrDefault(c.getOffreId(), candidatureMapper.toOffreResumeDegrade(c))));
    }

    /**
     * Propriété vérifiée auprès d'offre-service (l'offre peut n'avoir encore aucune candidature). Ordre par défaut :
     * la plus récente d'abord. Par score (choix explicite de l'entreprise, phase 8 e) : score indicatif décroissant,
     * les candidatures sans score à la fin, puis la plus récente.
     */
    public PageResponse<CandidatureEntrepriseResponse> listerCandidaturesOffre(Long entrepriseId, Long offreId,
                                                                               Pageable pageable, boolean parScore) {
        OffreInterne offre = offreClient.offre(offreId);
        if (!entrepriseId.equals(offre.entrepriseId())) {
            throw new AccesNonAutoriseException("Cette offre n'appartient pas à votre entreprise");
        }
        Pageable page = parScore
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Order.desc("analyse.score").nullsLast(), Sort.Order.desc("dateCandidature")))
                : pageable;
        return PageResponse.of(candidatureRepository.findByOffreId(offreId, page),
                c -> candidatureMapper.toEntrepriseResponse(c, iaActive));
    }

    /** La machine à états est appliquée par l'entité (→ 409 si transition invalide). Aucun appel réseau. */
    @Transactional
    @Audite(action = "CHANGEMENT_STATUT_CANDIDATURE", entite = CandidatureEtatAudit.ENTITE, idParam = "candidatureId")
    public CandidatureEntrepriseResponse changerStatut(Long entrepriseId, Long candidatureId,
                                                       StatutCandidature nouveauStatut) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new CandidatureNotFoundException(candidatureId));
        if (!candidature.concerneLEntreprise(entrepriseId)) {
            throw new AccesNonAutoriseException("Cette candidature ne concerne pas une offre de votre entreprise");
        }

        StatutCandidature ancienStatut = candidature.getStatut();
        candidature.changerStatut(nouveauStatut);

        // Écrit dans l'outbox, dans cette transaction : publié dans Kafka (puis email par notification-service)
        // seulement si le changement est enregistré, et jamais perdu s'il l'est
        Etudiant etudiant = candidature.getEtudiant();
        outbox.enregistrer(Topics.CANDIDATURE_STATUT_CHANGE, candidature.getId().toString(), new CandidatureStatutChangeEvent(
                candidature.getId(),
                etudiant.getEmail(),
                etudiant.getPrenom(),
                candidature.getOffreTitre(),
                candidature.getEntrepriseNom(),
                ancienStatut,
                nouveauStatut));
        return candidatureMapper.toEntrepriseResponse(candidature, iaActive);
    }
}
