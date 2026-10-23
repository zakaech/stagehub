package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;

/** Projection JPQL : nombre de candidatures pour un statut. */
public record StatutStat(StatutCandidature statut, Long nombre) {
}
