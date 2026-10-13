package com.stagehub.etudiant.entity;

import com.stagehub.auth.entity.Utilisateur;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Profil étudiant. Avec {@code @MapsId}, la clé primaire est celle de l'utilisateur :
 * l'id présent dans le JWT suffit pour retrouver l'étudiant, sans requête intermédiaire.
 */
@Entity
@Table(name = "etudiant")
@Getter
@Setter
public class Etudiant {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Utilisateur utilisateur;

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
