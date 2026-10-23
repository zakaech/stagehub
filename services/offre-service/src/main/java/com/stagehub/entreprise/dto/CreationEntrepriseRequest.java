package com.stagehub.entreprise.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Création d'un profil entreprise par auth-service (inscription) : l'id est celui du compte. */
public record CreationEntrepriseRequest(
        @NotNull Long id,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 150) String nom,
        @NotBlank @Size(max = 100) String secteur,
        @NotBlank @Size(max = 100) String ville,
        @Size(max = 2000) String description
) {
}
