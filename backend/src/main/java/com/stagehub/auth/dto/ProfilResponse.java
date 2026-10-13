package com.stagehub.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stagehub.auth.entity.Role;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.etudiant.dto.EtudiantResponse;

import java.time.Instant;

/** Profil de l'utilisateur connecté : seul le bloc correspondant à son rôle est renseigné. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfilResponse(
        Long id,
        String email,
        Role role,
        Instant dateCreation,
        EtudiantResponse etudiant,
        EntrepriseResponse entreprise
) {
}
