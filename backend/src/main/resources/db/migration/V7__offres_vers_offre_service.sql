-- Phase 7 (c) : entreprises et offres partent dans offre-service (sa propre base). Les candidatures gardent l'id de
-- l'offre, sans clé étrangère, et un instantané de l'offre au moment de postuler (dénormalisation volontaire) :
-- id de l'entreprise (règle de propriété locale), titre, nom de l'entreprise et ville (email, mode dégradé).

ALTER TABLE candidature ADD COLUMN entreprise_id  BIGINT;
ALTER TABLE candidature ADD COLUMN offre_titre    VARCHAR(150);
ALTER TABLE candidature ADD COLUMN entreprise_nom VARCHAR(150);
ALTER TABLE candidature ADD COLUMN offre_ville    VARCHAR(100);

UPDATE candidature c
SET entreprise_id = o.entreprise_id, offre_titre = o.titre, entreprise_nom = e.nom, offre_ville = o.ville
FROM offre o JOIN entreprise e ON e.id = o.entreprise_id
WHERE o.id = c.offre_id;

ALTER TABLE candidature ALTER COLUMN entreprise_id SET NOT NULL;
ALTER TABLE candidature ALTER COLUMN offre_titre SET NOT NULL;
ALTER TABLE candidature ALTER COLUMN entreprise_nom SET NOT NULL;

-- Nom donné par PostgreSQL à la contrainte « REFERENCES offre (id) » de V1
ALTER TABLE candidature DROP CONSTRAINT candidature_offre_id_fkey;
CREATE INDEX idx_candidature_entreprise ON candidature (entreprise_id);

-- Données désormais dans offre-service : journal et événements des validations d'entreprise compris
DELETE FROM audit_log WHERE entite = 'ENTREPRISE';
DELETE FROM outbox WHERE topic = 'stagehub.entreprise.validation';
DROP TABLE offre_technologie;
DROP TABLE offre;
DROP TABLE entreprise;
