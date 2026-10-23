package com.stagehub.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Une ligne du journal d'audit : qui a fait quoi, sur quelle entité, et l'état avant/après. */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action;

    /** Email de l'utilisateur authentifié (sub du JWT), ou "systeme" hors requête HTTP. */
    @Column(nullable = false)
    private String utilisateur;

    @Column(nullable = false)
    private String entite;

    @Column(name = "entite_id", nullable = false)
    private Long entiteId;

    @Column(name = "ancienne_valeur")
    private String ancienneValeur;

    @Column(name = "nouvelle_valeur")
    private String nouvelleValeur;

    @Column(nullable = false)
    private Instant date;
}
