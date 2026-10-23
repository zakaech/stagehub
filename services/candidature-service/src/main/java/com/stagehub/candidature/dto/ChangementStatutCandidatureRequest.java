package com.stagehub.candidature.dto;

import com.stagehub.candidature.entity.StatutCandidature;
import jakarta.validation.constraints.NotNull;

public record ChangementStatutCandidatureRequest(@NotNull StatutCandidature statut) {
}
