-- Événements déjà traités (consommateur idempotent). Kafka livre « au moins une fois » : un même événement peut
-- arriver deux fois (relais de l'outbox redémarré, nouvelle tentative...). Son id est inscrit ici dans la même
-- transaction que le traitement ; s'il y est déjà, le message est ignoré : jamais deux emails pour un événement.

CREATE TABLE evenement_traite (
    id               UUID         PRIMARY KEY,
    type             VARCHAR(100) NOT NULL,
    date_traitement  TIMESTAMP WITH TIME ZONE NOT NULL
);
