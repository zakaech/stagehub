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

export interface OffreResumeResponse {
  id: number;
  titre: string;
  entrepriseNom: string;
  ville: string;
  statut: StatutOffre;
}
