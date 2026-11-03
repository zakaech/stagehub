package com.stagehub.offre.dto;

import com.stagehub.offre.entity.StatutOffre;

import java.time.LocalDate;
import java.util.List;

/**
 * API interne (entre services). Le service des candidatures y lit ce qu'il faut pour qu'un étudiant postule : la règle
 * « offre OUVERTE et date limite non dépassée » est évaluée ici, par le service qui détient les offres
 * ({@code accepteCandidatures}), avec son horloge. ai-service y lit le contenu de l'offre à comparer au CV
 * (description, domaine, technologies). Champs ajoutés sans gêner les anciens lecteurs, qui ignorent
 * ce qu'ils ne connaissent pas.
 */
public record OffreInterneResponse(
        Long id,
        String titre,
        String ville,
        StatutOffre statut,
        LocalDate dateLimite,
        Long entrepriseId,
        String entrepriseNom,
        boolean accepteCandidatures,
        String description,
        String domaine,
        List<String> technologies
) {
}
