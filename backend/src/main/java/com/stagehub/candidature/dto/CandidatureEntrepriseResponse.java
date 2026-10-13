package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;

import java.time.Instant;

/** Vue d'une candidature côté entreprise : le profil du candidat. */
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
        Integer scoreMatching
) {
}
