package com.stagehub.etudiant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Profil étudiant. Sa clé primaire est l'id du compte, attribué par auth-service : l'id présent dans le
 * JWT suffit pour retrouver l'étudiant. Plus de clé étrangère : les comptes sont dans une autre base.
 */
@Entity
@Table(name = "etudiant")
@Getter
@Setter
public class Etudiant {

    @Id
    private Long id;

    /** Copie de l'email du compte (auth-service), pour les notifications. */
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String ecole;

    @Column(nullable = false)
    private String filiere;

    @Column(nullable = false)
    private String ville;

    @Column(name = "chemin_cv")
    private String cheminCv;
}
