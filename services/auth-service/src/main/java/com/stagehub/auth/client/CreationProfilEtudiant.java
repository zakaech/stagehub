package com.stagehub.auth.client;

/** Demande de création d'un profil étudiant : l'id est celui du compte, l'email sert aux notifications. */
public record CreationProfilEtudiant(
        Long id,
        String email,
        String nom,
        String prenom,
        String ecole,
        String filiere,
        String ville
) {
}
