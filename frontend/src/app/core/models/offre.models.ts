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

export interface OffreResumeResponse {
  id: number;
  titre: string;
  entrepriseNom: string;
  ville: string;
  statut: StatutOffre;
}
