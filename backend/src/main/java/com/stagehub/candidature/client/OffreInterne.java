package com.stagehub.candidature.client;

import java.time.LocalDate;

/**
 * Offre telle que renvoyée par offre-service ({@code GET /internal/offres/{id}}). Copie propre à ce service du contrat
 * (aucun module partagé) ; la règle « peut-on postuler ? » est évaluée par offre-service ({@code accepteCandidatures}).
 */
public record OffreInterne(
        Long id,
        String titre,
        String ville,
        String statut,
        LocalDate dateLimite,
        Long entrepriseId,
        String entrepriseNom,
        boolean accepteCandidatures
) {
}
