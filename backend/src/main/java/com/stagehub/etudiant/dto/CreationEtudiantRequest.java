package com.stagehub.etudiant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Création d'un profil par auth-service (inscription) : l'id est celui du compte qu'il vient de créer. */
public record CreationEtudiantRequest(
        @NotNull Long id,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @NotBlank @Size(max = 150) String ecole,
        @NotBlank @Size(max = 150) String filiere,
        @NotBlank @Size(max = 100) String ville
) {
}
