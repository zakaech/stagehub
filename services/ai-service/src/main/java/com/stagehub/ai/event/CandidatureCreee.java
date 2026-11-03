package com.stagehub.ai.event;

/**
 * Message du topic {@code stagehub.candidature.creee}. Copie propre à ce service du contrat publié par
 * candidature-service (aucun module partagé) : les champs inconnus sont ignorés (« tolerant reader »).
 *
 * @param cvFichier nom du CV figé dans la candidature (null si l'étudiant n'en avait pas) : son texte
 *                  est comparé à celui de l'offre
 */
public record CandidatureCreee(
        Long candidatureId,
        Long offreId,
        Long etudiantId,
        String cvFichier
) {
}
