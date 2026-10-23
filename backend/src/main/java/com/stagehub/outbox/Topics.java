package com.stagehub.outbox;

/**
 * Topics Kafka des événements publiés par le monolithe (créés au démarrage par le conteneur kafka-init, avec leur
 * topic d'erreurs « .DLT »). Contrat partagé avec les consommateurs : ne pas renommer sans les prévenir.
 */
public final class Topics {

    public static final String CANDIDATURE_STATUT_CHANGE = "stagehub.candidature.statut-change";
    public static final String ENTREPRISE_VALIDATION = "stagehub.entreprise.validation";

    /** En-tête portant l'id unique de l'événement : le consommateur s'en sert pour ignorer les doublons. */
    public static final String ENTETE_ID_EVENEMENT = "stagehub-evenement-id";
    public static final String ENTETE_TYPE = "stagehub-type";

    private Topics() {
    }
}
