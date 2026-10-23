package com.stagehub.candidature.dto;

/**
 * Résumé d'une offre, embarqué dans les candidatures d'un étudiant. Lu dans offre-service (à jour) ; si ce service ne
 * répond pas, reconstruit à partir des données recopiées dans la candidature, avec un statut inconnu (null).
 */
public record OffreResumeResponse(
        Long id,
        String titre,
        String entrepriseNom,
        String ville,
        String statut
) {
}
