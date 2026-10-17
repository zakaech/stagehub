package com.stagehub.audit.entity;

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

/** Une ligne du journal d'audit : qui a fait quoi, sur quelle entité, et l'état avant/après. */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionAudit action;

    /** Email de l'utilisateur authentifié (sub du JWT), ou "systeme" hors requête HTTP. */
    @Column(nullable = false)
    private String utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntiteAuditee entite;

    @Column(name = "entite_id", nullable = false)
    private Long entiteId;

    @Column(name = "ancienne_valeur")
    private String ancienneValeur;

    @Column(name = "nouvelle_valeur")
    private String nouvelleValeur;

    @Column(nullable = false)
    private Instant date;
}
