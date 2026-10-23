-- Phase 7 (b) : les comptes (email, mot de passe, rôle) partent dans auth-service, qui a sa propre base.
-- Les profils gardent l'id du compte comme clé primaire, mais sans clé étrangère (la table est dans une autre base),
-- et reçoivent une copie de l'email : le monolithe en a besoin pour les notifications.

ALTER TABLE etudiant ADD COLUMN email VARCHAR(255);
UPDATE etudiant e SET email = u.email FROM utilisateur u WHERE u.id = e.id;
ALTER TABLE etudiant ALTER COLUMN email SET NOT NULL;

ALTER TABLE entreprise ADD COLUMN email VARCHAR(255);
UPDATE entreprise e SET email = u.email FROM utilisateur u WHERE u.id = e.id;
ALTER TABLE entreprise ALTER COLUMN email SET NOT NULL;

-- Noms donnés par PostgreSQL aux contraintes "REFERENCES utilisateur (id)" de V1
ALTER TABLE etudiant DROP CONSTRAINT etudiant_id_fkey;
ALTER TABLE entreprise DROP CONSTRAINT entreprise_id_fkey;

DROP TABLE utilisateur;
