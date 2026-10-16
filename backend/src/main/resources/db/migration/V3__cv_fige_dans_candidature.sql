-- Phase 3 : la candidature fige le CV en vigueur au moment où l'étudiant postule.
-- L'entreprise télécharge ce fichier-là (et la phase 8 calculera le score sur le même),
-- même si l'étudiant remplace son CV ensuite. NULL : l'étudiant n'avait pas de CV.
ALTER TABLE candidature ADD COLUMN cv_fichier VARCHAR(100);

-- Au remplacement d'un CV, on vérifie qu'aucune candidature ne référence l'ancien fichier avant de le supprimer.
-- Index partiel : seules les candidatures avec CV y figurent.
CREATE INDEX idx_candidature_cv_fichier ON candidature (cv_fichier) WHERE cv_fichier IS NOT NULL;
