package com.stagehub.offre.dto;

import com.stagehub.offre.entity.StatutOffre;

import java.time.LocalDate;

/**
 * Ce dont le service des candidatures a besoin pour qu'un étudiant postule : la règle « offre OUVERTE et date limite
 * non dépassée » est évaluée ici, par le service qui détient les offres ({@code accepteCandidatures}), avec son horloge.
 */
public record OffreInterneResponse(
        Long id,
        String titre,
        String ville,
        StatutOffre statut,
        LocalDate dateLimite,
        Long entrepriseId,
        String entrepriseNom,
        boolean accepteCandidatures
) {
}
