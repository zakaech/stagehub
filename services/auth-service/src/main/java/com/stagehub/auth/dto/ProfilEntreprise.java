package com.stagehub.auth.dto;

/** Profil entreprise tel que renvoyé par le service qui le détient (statut de validation compris). */
public record ProfilEntreprise(
        Long id,
        String email,
        String nom,
        String secteur,
        String ville,
        String description,
        String statutValidation
) {
}
