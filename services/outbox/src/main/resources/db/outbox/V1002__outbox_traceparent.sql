-- Phase 8 (a) : contexte de trace de l'action qui a produit l'événement (en-tête W3C « traceparent »,
-- ex. 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01). Le relais publie plus tard, depuis un autre thread :
-- sans cette colonne, la trace s'arrêterait à l'outbox. Vide si l'action n'avait pas de trace (tâche planifiée...).
ALTER TABLE outbox ADD COLUMN traceparent VARCHAR(64);
