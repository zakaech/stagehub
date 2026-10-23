package com.stagehub.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mot de passe limité à 72 caractères : au-delà, BCrypt ignore silencieusement les octets suivants. */
public record RegisterEtudiantRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String motDePasse,
        @NotBlank @Size(max = 100) String nom,
        @NotBlank @Size(max = 100) String prenom,
        @NotBlank @Size(max = 150) String ecole,
        @NotBlank @Size(max = 150) String filiere,
        @NotBlank @Size(max = 100) String ville
) {
}
