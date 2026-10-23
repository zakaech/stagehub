package com.stagehub.admin.dto;

import com.stagehub.candidature.entity.StatutCandidature;

import java.util.Map;

/**
 * Partie « candidatures » des statistiques de l'admin (phase 7 c). La page Angular la combine avec la partie
 * « offres et entreprises », fournie par offre-service.
 */
public record StatistiquesCandidaturesResponse(
        long nombreCandidatures,
        Map<StatutCandidature, Long> candidaturesParStatut
) {
}
