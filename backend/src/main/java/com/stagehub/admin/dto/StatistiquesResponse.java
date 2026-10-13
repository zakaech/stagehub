package com.stagehub.admin.dto;

import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.offre.dto.TechnologieStat;

import java.util.List;
import java.util.Map;

public record StatistiquesResponse(
        long nombreOffres,
        long nombreOffresOuvertes,
        long nombreCandidatures,
        Map<StatutCandidature, Long> candidaturesParStatut,
        List<TechnologieStat> topTechnologies,
        long entreprisesEnAttente
) {
}
