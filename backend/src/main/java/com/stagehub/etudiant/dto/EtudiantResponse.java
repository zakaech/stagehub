package com.stagehub.etudiant.dto;

public record EtudiantResponse(
        Long id,
        String nom,
        String prenom,
        String ecole,
        String filiere,
        String ville,
        boolean cvDisponible
) {
}
