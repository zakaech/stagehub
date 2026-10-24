package com.stagehub.candidature.event;

/**
 * Publié quand un étudiant postule : message Kafka du topic {@code stagehub.candidature.creee} (JSON, écrit dans
 * l'outbox puis relayé), consommé par ai-service qui calculera en phase 8 le score de matching, sans faire attendre
 * l'étudiant. Contrat partagé : ajouter un champ est sans risque, en retirer ou en renommer un casse ai-service.
 *
 * @param cvFichier CV figé dans la candidature (null si l'étudiant n'en avait pas)
 */
public record CandidatureCreeeEvent(
        Long candidatureId,
        Long offreId,
        Long etudiantId,
        String cvFichier
) {
}
