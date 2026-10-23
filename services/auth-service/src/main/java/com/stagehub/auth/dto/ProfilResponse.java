package com.stagehub.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stagehub.auth.entity.Role;

import java.time.Instant;

/**
 * Profil de l'utilisateur connecté, même contrat que le monolithe : le compte (auth-service) + le bloc du profil
 * correspondant à son rôle, lu dans le service qui le détient.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfilResponse(
        Long id,
        String email,
        Role role,
        Instant dateCreation,
        ProfilEtudiant etudiant,
        ProfilEntreprise entreprise
) {
}
