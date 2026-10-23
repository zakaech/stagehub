package com.stagehub.auth.client;

/** Demande de création d'un profil entreprise (créé EN_ATTENTE de validation par l'admin). */
public record CreationProfilEntreprise(
        Long id,
        String email,
        String nom,
        String secteur,
        String ville,
        String description
) {
}
