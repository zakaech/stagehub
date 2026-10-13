/** Miroir TypeScript des DTOs Java de l'administration. */

import { StatutCandidature } from './candidature.models';

export interface TechnologieStat {
  technologie: string;
  nombreOffres: number;
}

export interface StatistiquesResponse {
  nombreOffres: number;
  nombreOffresOuvertes: number;
  nombreCandidatures: number;
  candidaturesParStatut: Record<StatutCandidature, number>;
  topTechnologies: TechnologieStat[];
  entreprisesEnAttente: number;
}
