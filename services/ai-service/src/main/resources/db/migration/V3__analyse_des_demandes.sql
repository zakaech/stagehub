-- Phase 8 (d) : les demandes sont analysées par une tâche planifiée (AnalyseCandidatureService), une à la fois.
--   EN_ATTENTE → EN_COURS → TERMINEE, ou ECHEC (définitif, ou après le nombre maximal de tentatives).
-- Une demande EN_COURS depuis trop longtemps (service arrêté pendant l'analyse) est reprise : debut_analyse sert de
-- « bail ». Le résultat lui-même (score, résumé) n'est pas gardé ici : il part vers candidature-service, qui l'affiche.

ALTER TABLE demande_analyse DROP CONSTRAINT ck_demande_analyse_statut;
ALTER TABLE demande_analyse ADD CONSTRAINT ck_demande_analyse_statut
    CHECK (statut IN ('EN_ATTENTE', 'EN_COURS', 'TERMINEE', 'ECHEC'));

ALTER TABLE demande_analyse
    -- Tentatives déjà commencées (nouvelle tentative après une panne passagère : Ollama, réseau...)
    ADD COLUMN tentatives          INTEGER      NOT NULL DEFAULT 0,
    -- Pas avant cette date (attente croissante entre deux tentatives) ; vide = dès que possible
    ADD COLUMN prochaine_tentative TIMESTAMP WITH TIME ZONE,
    ADD COLUMN debut_analyse       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN date_fin            TIMESTAMP WITH TIME ZONE,
    -- Code de la cause d'un échec (CV_ABSENT, CV_ILLISIBLE, OFFRE_INTROUVABLE, ANALYSE_IMPOSSIBLE)
    ADD COLUMN motif_echec         VARCHAR(40),
    ADD COLUMN modele              VARCHAR(100),
    ADD COLUMN duree_ms            BIGINT;

-- La tâche planifiée cherche les demandes à traiter, de la plus ancienne à la plus récente
CREATE INDEX idx_demande_analyse_a_traiter ON demande_analyse (date_reception) WHERE statut IN ('EN_ATTENTE', 'EN_COURS');
