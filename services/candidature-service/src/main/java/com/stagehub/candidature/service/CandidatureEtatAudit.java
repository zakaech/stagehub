package com.stagehub.candidature.service;

import com.stagehub.audit.aspect.LecteurEtatAudit;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** État audité d'une candidature : son statut. */
@Component
public class CandidatureEtatAudit implements LecteurEtatAudit {

    /** Nom de l'entité dans le journal d'audit (?entite=CANDIDATURE). */
    public static final String ENTITE = "CANDIDATURE";

    private final CandidatureRepository candidatureRepository;

    public CandidatureEtatAudit(CandidatureRepository candidatureRepository) {
        this.candidatureRepository = candidatureRepository;
    }

    @Override
    public String entite() {
        return ENTITE;
    }

    @Override
    public Optional<String> lireEtat(Long id) {
        return candidatureRepository.findById(id).map(candidature -> candidature.getStatut().name());
    }
}
