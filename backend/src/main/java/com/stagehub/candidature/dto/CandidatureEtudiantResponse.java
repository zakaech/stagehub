package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.offre.dto.OffreResumeResponse;

import java.time.Instant;

/**
 * Vue d'une candidature côté étudiant. Le résumé de l'offre (dont son statut) reste disponible
 * même quand l'offre est fermée et n'est plus consultable publiquement.
 */
public record CandidatureEtudiantResponse(
        Long id,
        OffreResumeResponse offre,
        String lettreMotivation,
        StatutCandidature statut,
        Instant dateCandidature
) {
}
