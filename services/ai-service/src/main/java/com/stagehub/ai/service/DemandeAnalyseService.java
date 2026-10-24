package com.stagehub.ai.service;

import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.event.CandidatureCreee;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import com.stagehub.ai.repository.EvenementTraiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Enregistre une demande d'analyse par candidature créée, <b>une seule fois</b> par événement (consommateur
 * idempotent, comme notification-service) : l'id de l'événement est inscrit dans la même transaction que la demande ;
 * s'il l'est déjà, le message est ignoré. En phase 8, l'analyse partira d'ici, en asynchrone : la candidature
 * n'attend pas le score.
 */
@Service
public class DemandeAnalyseService {

    private static final Logger log = LoggerFactory.getLogger(DemandeAnalyseService.class);

    private final EvenementTraiteRepository evenementTraiteRepository;
    private final DemandeAnalyseRepository demandeAnalyseRepository;
    private final Clock clock;

    public DemandeAnalyseService(EvenementTraiteRepository evenementTraiteRepository,
                                 DemandeAnalyseRepository demandeAnalyseRepository, Clock clock) {
        this.evenementTraiteRepository = evenementTraiteRepository;
        this.demandeAnalyseRepository = demandeAnalyseRepository;
        this.clock = clock;
    }

    @Transactional
    public void surCandidatureCreee(UUID evenementId, CandidatureCreee evenement) {
        if (evenementTraiteRepository.inscrire(evenementId, "CandidatureCreee", clock.instant()) == 0) {
            log.info("Événement {} (CandidatureCreee) déjà traité : doublon ignoré", evenementId);
            return;
        }
        DemandeAnalyse demande = new DemandeAnalyse();
        demande.setCandidatureId(evenement.candidatureId());
        demande.setOffreId(evenement.offreId());
        demande.setEtudiantId(evenement.etudiantId());
        demande.setCvFichier(evenement.cvFichier());
        demande.setDateReception(clock.instant());
        demandeAnalyseRepository.save(demande);
        log.info("Candidature {} enregistrée pour analyse ({})", evenement.candidatureId(),
                evenement.cvFichier() == null ? "sans CV" : "avec CV");
    }
}
