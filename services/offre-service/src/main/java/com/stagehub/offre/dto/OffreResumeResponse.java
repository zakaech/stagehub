package com.stagehub.offre.dto;

import com.stagehub.offre.entity.StatutOffre;

/** Résumé d'une offre, embarqué dans d'autres réponses (ex. les candidatures d'un étudiant). */
public record OffreResumeResponse(
        Long id,
        String titre,
        String entrepriseNom,
        String ville,
        StatutOffre statut
) {
}
