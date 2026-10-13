package com.stagehub.offre.dto;

import com.stagehub.offre.entity.StatutOffre;
import jakarta.validation.constraints.NotNull;

public record ChangementStatutOffreRequest(@NotNull StatutOffre statut) {
}
