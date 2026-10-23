package com.stagehub.entreprise.service;

import com.stagehub.audit.aspect.LecteurEtatAudit;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** État audité d'une entreprise : son statut de validation. */
@Component
public class EntrepriseEtatAudit implements LecteurEtatAudit {

    private final EntrepriseRepository entrepriseRepository;

    public EntrepriseEtatAudit(EntrepriseRepository entrepriseRepository) {
        this.entrepriseRepository = entrepriseRepository;
    }

    public static final String ENTITE = "ENTREPRISE";

    @Override
    public String entite() {
        return ENTITE;
    }

    @Override
    public Optional<String> lireEtat(Long id) {
        return entrepriseRepository.findById(id).map(entreprise -> entreprise.getStatutValidation().name());
    }
}
