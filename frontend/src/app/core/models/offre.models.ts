/** Miroir TypeScript des DTOs Java du package com.stagehub.offre. */

export type StatutOffre = 'OUVERTE' | 'FERMEE';

export interface OffreResponse {
  id: number;
  titre: string;
  description: string;
  ville: string;
  domaine: string;
  technologies: string[];
  dureeMois: number;
  /** Date au format ISO "AAAA-MM-JJ". */
  dateLimite: string;
  statut: StatutOffre;
  entrepriseId: number;
  entrepriseNom: string;
}

export interface OffreRequest {
  titre: string;
  description: string;
  ville: string;
  domaine: string;
  technologies: string[];
  dureeMois: number;
  dateLimite: string;
}

/** Critères de recherche (tous facultatifs) : ET entre critères, « au moins une » technologie. */
export interface OffreFiltre {
  ville: string | null;
  domaine: string | null;
  technologies: string[];
}

export interface FiltresDisponibles {
  villes: string[];
  domaines: string[];
  technologies: string[];
}

/**
 * Résumé d'une offre dans « mes candidatures ». Phase 7 (c) : lu dans offre-service ; s'il ne répond pas, le titre,
 * l'entreprise et la ville viennent de l'instantané pris au moment de postuler, et le statut est inconnu (null).
 */
export interface OffreResumeResponse {
  id: number;
  titre: string;
  entrepriseNom: string;
  ville: string;
  statut: StatutOffre | null;
}
