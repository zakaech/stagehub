package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;

import java.time.Instant;

/** Vue d'une candidature côté entreprise : le profil du candidat, et l'analyse par l'IA (phase 8, indicative). */
public record CandidatureEntrepriseResponse(
        Long id,
        Long offreId,
        String offreTitre,
        Long etudiantId,
        String etudiantNom,
        String etudiantPrenom,
        String etudiantEmail,
        String etudiantEcole,
        String etudiantFiliere,
        String lettreMotivation,
        StatutCandidature statut,
        Instant dateCandidature,
        // Null pour une candidature antérieure à la phase 8
        AnalyseIaResponse analyse,
        // CV figé au moment de la candidature, téléchargeable via /entreprise/candidatures/{id}/cv
        boolean cvDisponible
) {
}
