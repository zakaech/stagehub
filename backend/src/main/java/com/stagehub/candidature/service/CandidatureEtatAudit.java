package com.stagehub.candidature.service;

import com.stagehub.audit.aspect.LecteurEtatAudit;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** État audité d'une candidature : son statut. */
@Component
public class CandidatureEtatAudit implements LecteurEtatAudit {

    private final CandidatureRepository candidatureRepository;

    public CandidatureEtatAudit(CandidatureRepository candidatureRepository) {
        this.candidatureRepository = candidatureRepository;
    }

    @Override
    public EntiteAuditee entite() {
        return EntiteAuditee.CANDIDATURE;
    }

    @Override
    public Optional<String> lireEtat(Long id) {
        return candidatureRepository.findById(id).map(candidature -> candidature.getStatut().name());
    }
}
