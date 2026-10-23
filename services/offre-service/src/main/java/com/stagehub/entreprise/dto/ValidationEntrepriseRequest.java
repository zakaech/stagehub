package com.stagehub.entreprise.dto;

import com.stagehub.entreprise.entity.StatutValidation;
import jakarta.validation.constraints.NotNull;

/** Décision de l'admin : VALIDEE ou REFUSEE. */
public record ValidationEntrepriseRequest(@NotNull StatutValidation decision) {
}
