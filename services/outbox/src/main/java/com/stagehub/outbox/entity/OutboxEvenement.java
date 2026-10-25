package com.stagehub.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Événement en attente de publication dans Kafka (ou déjà publié si datePublication est renseignée). */
@Entity
@Table(name = "outbox")
@Getter
@Setter
public class OutboxEvenement {

    /** Id unique de l'événement, transmis dans un en-tête Kafka (idempotence du consommateur). */
    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String cle;

    @Column(nullable = false)
    private String type;

    /** Événement sérialisé en JSON. */
    @Column(nullable = false)
    private String contenu;

    @Column(name = "date_creation", nullable = false)
    private Instant dateCreation;

    @Column(name = "date_publication")
    private Instant datePublication;

    /** Contexte de trace W3C de l'action d'origine, remis dans l'en-tête du message Kafka (null : pas de trace). */
    private String traceparent;
}
