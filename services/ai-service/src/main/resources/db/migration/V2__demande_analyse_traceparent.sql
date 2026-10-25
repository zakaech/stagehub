-- Phase 8 (a) : contexte de trace W3C reçu avec l'événement (en-tête « traceparent »). L'analyse partira plus tard,
-- d'une tâche planifiée (sous-étape d) : elle reprendra ce contexte, et ses logs garderont le traceId de la
-- candidature d'origine. Vide si le message n'avait pas de trace.
ALTER TABLE demande_analyse ADD COLUMN traceparent VARCHAR(64);
