-- Phase 8 (e) : état et résultat de l'analyse de la candidature par l'IA, reçus d'ai-service par Kafka
-- (topic stagehub.candidature.analyse). Le score lui-même garde la colonne prévue dès la phase 1 (score_matching).
-- Une candidature antérieure à la phase 8 garde analyse_statut vide : elle n'a jamais été envoyée à l'analyse.

ALTER TABLE candidature
    ADD COLUMN analyse_statut              VARCHAR(20),
    ADD COLUMN analyse_motif_echec         VARCHAR(40),
    -- Nouvelle tentative programmée par ai-service après une panne (statut EN_ATTENTE)
    ADD COLUMN analyse_prochaine_tentative TIMESTAMP WITH TIME ZONE,
    ADD COLUMN analyse_justification       VARCHAR(600),
    -- Lignes du résumé, puis technologies retrouvées : une par ligne (aucune ne contient de saut de ligne)
    ADD COLUMN analyse_resume              TEXT,
    ADD COLUMN technologies_trouvees       TEXT,
    ADD COLUMN technologies_offre          INTEGER,
    ADD COLUMN alerte_ecart                BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN contenu_suspect             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN analyse_modele              VARCHAR(100),
    ADD COLUMN date_analyse                TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT ck_candidature_analyse_statut
        CHECK (analyse_statut IN ('EN_ATTENTE', 'EN_COURS', 'TERMINEE', 'ECHEC'));

-- Tri « par score indicatif » des candidatures d'une offre (les candidatures sans score à la fin)
CREATE INDEX idx_candidature_offre_score ON candidature (offre_id, score_matching DESC NULLS LAST, date_candidature DESC);

-- Événements Kafka déjà traités : un état d'analyse reçu deux fois (livraison « au moins une fois ») est ignoré
CREATE TABLE evenement_traite (
    id               UUID         PRIMARY KEY,
    type             VARCHAR(100) NOT NULL,
    date_traitement  TIMESTAMP WITH TIME ZONE NOT NULL
);
