package com.stagehub.common;

/**
 * Topics Kafka des événements publiés par candidature-service (créés par le conteneur kafka-init, avec leur topic
 * d'erreurs « .DLT »). Contrat partagé avec notification-service : ne pas renommer sans le prévenir. Les en-têtes des
 * messages (id d'événement, type) sont définis par la bibliothèque outbox (OutboxEntetes).
 */
public final class Topics {

    public static final String CANDIDATURE_STATUT_CHANGE = "stagehub.candidature.statut-change";

    private Topics() {
    }
}
