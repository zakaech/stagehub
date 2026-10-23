package com.stagehub.candidature.event;

import com.stagehub.candidature.entity.StatutCandidature;

/**
 * Publié quand une entreprise change le statut d'une candidature : message Kafka du topic
 * {@code stagehub.candidature.statut-change} (JSON, écrit dans l'outbox puis relayé). Il contient tout ce dont
 * l'email a besoin : notification-service n'a pas accès à la base de candidature-service et ne la rappelle pas.
 * Contrat partagé : ajouter un champ est sans risque (les consommateurs ignorent ce qu'ils ne connaissent pas),
 * en retirer ou en renommer un casse notification-service.
 */
public record CandidatureStatutChangeEvent(
        Long candidatureId,
        String etudiantEmail,
        String etudiantPrenom,
        String offreTitre,
        String entrepriseNom,
        StatutCandidature ancienStatut,
        StatutCandidature nouveauStatut
) {
}
