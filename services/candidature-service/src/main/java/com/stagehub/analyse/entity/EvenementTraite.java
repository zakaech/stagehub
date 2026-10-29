package com.stagehub.analyse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Événement Kafka déjà traité (consommateur idempotent, même principe que notification-service et ai-service). */
@Entity
@Table(name = "evenement_traite")
@Getter
@Setter
public class EvenementTraite {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(name = "date_traitement", nullable = false)
    private Instant dateTraitement;
}
