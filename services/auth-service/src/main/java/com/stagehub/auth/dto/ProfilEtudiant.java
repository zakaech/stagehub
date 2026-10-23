package com.stagehub.auth.dto;

/**
 * Profil étudiant tel que renvoyé par le service qui le détient. auth-service a sa propre copie du contrat (pas de
 * module partagé) : il le relaie sans l'interpréter.
 */
public record ProfilEtudiant(
        Long id,
        String nom,
        String prenom,
        String ecole,
        String filiere,
        String ville,
        boolean cvDisponible
) {
}
