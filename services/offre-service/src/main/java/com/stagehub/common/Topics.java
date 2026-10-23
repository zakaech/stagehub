package com.stagehub.common;

/**
 * Topics Kafka des événements publiés par offre-service (créés par kafka-init avec leur topic d'erreurs « .DLT »).
 * Contrat partagé avec les consommateurs (notification-service) : ne pas renommer sans les prévenir.
 */
public final class Topics {

    public static final String ENTREPRISE_VALIDATION = "stagehub.entreprise.validation";

    private Topics() {
    }
}
