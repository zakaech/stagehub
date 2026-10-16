-- Phase 3 : filtres des offres (ville, domaine, technologies), insensibles à la casse.

-- 1. Nettoyage des valeurs existantes, avec la même règle que Textes.nettoyer :
--    espaces autour retirés, suites d'espaces internes réduites à un seul.
UPDATE offre
SET ville   = regexp_replace(btrim(ville), '\s+', ' ', 'g'),
    domaine = regexp_replace(btrim(domaine), '\s+', ' ', 'g');

-- Technologies : on supprime d'abord les doublons qui ne diffèrent que par la casse ou les espaces
-- (on garde la plus petite valeur), sinon la mise à jour violerait la clé primaire (offre_id, technologie).
DELETE FROM offre_technologie a
USING offre_technologie b
WHERE a.offre_id = b.offre_id
  AND a.technologie > b.technologie
  AND lower(regexp_replace(btrim(a.technologie), '\s+', ' ', 'g'))
    = lower(regexp_replace(btrim(b.technologie), '\s+', ' ', 'g'));

UPDATE offre_technologie
SET technologie = regexp_replace(btrim(technologie), '\s+', ' ', 'g')
WHERE technologie <> regexp_replace(btrim(technologie), '\s+', ' ', 'g');

-- 2. Index sur expression : les filtres comparent lower(colonne) = lower(:valeur) ;
--    un index classique sur la colonne ne servirait pas à cette requête.
CREATE INDEX idx_offre_ville_lower ON offre (lower(ville));
CREATE INDEX idx_offre_domaine_lower ON offre (lower(domaine));
CREATE INDEX idx_offre_technologie_lower ON offre_technologie (lower(technologie));
