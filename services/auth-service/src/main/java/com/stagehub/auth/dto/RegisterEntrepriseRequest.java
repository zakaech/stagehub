package com.stagehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterEntrepriseRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String motDePasse,
        @NotBlank @Size(max = 150) String nom,
        @NotBlank @Size(max = 100) String secteur,
        @NotBlank @Size(max = 100) String ville,
        @Size(max = 2000) String description
) {
}
