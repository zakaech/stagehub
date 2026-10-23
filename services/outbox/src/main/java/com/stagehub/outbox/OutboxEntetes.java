package com.stagehub.outbox;

/** En-têtes ajoutés par le relais à chaque message Kafka (contrat avec les consommateurs). */
public final class OutboxEntetes {

    /** Id unique de l'événement : le consommateur s'en sert pour ignorer les doublons (livraison « au moins une fois »). */
    public static final String ID_EVENEMENT = "stagehub-evenement-id";
    /** Nom simple de la classe de l'événement (ex. EntrepriseValidationEvent), pour le diagnostic. */
    public static final String TYPE = "stagehub-type";

    private OutboxEntetes() {
    }
}
