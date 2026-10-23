package com.stagehub.notification.event;

/**
 * Message du topic {@code stagehub.candidature.statut-change}. Copie propre à ce service du contrat publié par le
 * service des candidatures (aucun module partagé) : les champs inconnus sont ignorés (« tolerant reader »), les
 * statuts sont lus comme du texte — un nouveau statut côté producteur ne casse pas la lecture.
 */
public record CandidatureStatutChange(
        Long candidatureId,
        String etudiantEmail,
        String etudiantPrenom,
        String offreTitre,
        String entrepriseNom,
        String ancienStatut,
        String nouveauStatut
) {
}
