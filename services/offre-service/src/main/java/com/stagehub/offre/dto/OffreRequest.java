package com.stagehub.offre.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

/** Création et modification d'une offre (même contrat pour POST et PUT). */
public record OffreRequest(
        @NotBlank @Size(max = 150) String titre,
        @NotBlank @Size(max = 5000) String description,
        @NotBlank @Size(max = 100) String ville,
        @NotBlank @Size(max = 100) String domaine,
        @NotEmpty @Size(max = 20) Set<@NotBlank @Size(max = 50) String> technologies,
        @NotNull @Min(1) @Max(12) Integer dureeMois,
        @NotNull @FutureOrPresent LocalDate dateLimite
) {
}
