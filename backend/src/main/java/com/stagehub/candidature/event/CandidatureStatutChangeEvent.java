package com.stagehub.candidature.event;

import com.stagehub.candidature.entity.StatutCandidature;

/**
 * Publié quand une entreprise change le statut d'une candidature. L'événement contient déjà tout ce dont
 * l'email a besoin : le listener, exécuté dans un autre thread après le commit, n'a ni session Hibernate
 * (pas de lazy loading possible) ni SecurityContext (propre au thread de la requête).
 * En phase 7, ce sera le message Kafka CandidatureStatutChange.
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
