package com.stagehub.outbox;

/** En-têtes ajoutés par le relais à chaque message Kafka (contrat avec les consommateurs). */
public final class OutboxEntetes {

    /** Id unique de l'événement : le consommateur s'en sert pour ignorer les doublons (livraison « au moins une fois »). */
    public static final String ID_EVENEMENT = "stagehub-evenement-id";
    /** Nom simple de la classe de l'événement (ex. EntrepriseValidationEvent), pour le diagnostic. */
    public static final String TYPE = "stagehub-type";
    /**
     * Contexte de trace W3C (standard, lu par Micrometer Tracing côté consommateur) : le traitement du message
     * continue la trace de l'action qui l'a produit, avec le même traceId.
     */
    public static final String TRACEPARENT = "traceparent";

    private OutboxEntetes() {
    }
}
