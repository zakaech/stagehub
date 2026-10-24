-- Base d'ai-service (phase 7 f). Événements déjà traités (consommateur idempotent, même principe que
-- notification-service) et demandes d'analyse, une par candidature créée (traitées en phase 8).

CREATE TABLE evenement_traite (
    id               UUID         PRIMARY KEY,
    type             VARCHAR(100) NOT NULL,
    date_traitement  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE demande_analyse (
    -- Id de la candidature dans candidature-service (autre base : pas de clé étrangère)
    candidature_id  BIGINT       PRIMARY KEY,
    offre_id        BIGINT       NOT NULL,
    etudiant_id     BIGINT       NOT NULL,
    cv_fichier      VARCHAR(100),
    statut          VARCHAR(20)  NOT NULL,
    date_reception  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_demande_analyse_statut CHECK (statut IN ('EN_ATTENTE'))
);
