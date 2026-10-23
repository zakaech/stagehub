package com.stagehub.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

/**
 * Compte StageHub. Son id est aussi celui du profil étudiant ou entreprise dans l'autre service (même principe que
 * le {@code @MapsId} du monolithe, mais sans clé étrangère : les deux tables sont dans deux bases différentes).
 */
@Entity
@Table(name = "utilisateur")
@Getter
@Setter
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutCompte statut;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private Instant dateCreation;

    public boolean estActif() {
        return statut == StatutCompte.ACTIF;
    }

    /** Les emails sont stockés et recherchés en minuscules, sans espaces autour. */
    public static String normaliserEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
