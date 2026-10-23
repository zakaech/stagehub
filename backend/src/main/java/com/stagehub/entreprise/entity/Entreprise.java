package com.stagehub.entreprise.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "entreprise")
@Getter
@Setter
public class Entreprise {

    @Id
    private Long id;

    /** Copie de l'email du compte (auth-service), pour les notifications. */
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String secteur;

    @Column(nullable = false)
    private String ville;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_validation", nullable = false)
    private StatutValidation statutValidation = StatutValidation.EN_ATTENTE;

    public boolean estValidee() {
        return statutValidation == StatutValidation.VALIDEE;
    }
}
