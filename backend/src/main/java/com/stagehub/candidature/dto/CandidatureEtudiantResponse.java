package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;

import java.time.Instant;

/** Vue d'une candidature côté étudiant : l'offre visée et l'avancement. */
public record CandidatureEtudiantResponse(
        Long id,
        Long offreId,
        String offreTitre,
        String entrepriseNom,
        String lettreMotivation,
        StatutCandidature statut,
        Instant dateCandidature
) {
}
