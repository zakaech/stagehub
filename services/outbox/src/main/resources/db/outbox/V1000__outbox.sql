-- Table de la bibliothèque outbox (numéro 1000 : à part des migrations propres à chaque service).
-- Phase 7 (e) : « transactional outbox ». Un événement à publier dans Kafka est d'abord écrit ici, dans la même
-- transaction que le changement métier : les deux sont enregistrés, ou aucun. Le relais (OutboxRelais) le publie
-- ensuite dans Kafka et remplit date_publication.

CREATE TABLE outbox (
    id                UUID         PRIMARY KEY,
    topic             VARCHAR(200) NOT NULL,
    -- Clé Kafka (id de l'agrégat) : les événements d'une même candidature restent dans l'ordre (même partition)
    cle               VARCHAR(100) NOT NULL,
    type              VARCHAR(100) NOT NULL,
    contenu           TEXT         NOT NULL,
    date_creation     TIMESTAMP WITH TIME ZONE NOT NULL,
    date_publication  TIMESTAMP WITH TIME ZONE
);

-- Le relais ne lit que les événements pas encore publiés, du plus ancien au plus récent : index partiel
CREATE INDEX idx_outbox_a_publier ON outbox (date_creation) WHERE date_publication IS NULL;
