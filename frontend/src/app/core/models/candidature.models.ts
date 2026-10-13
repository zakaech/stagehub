/** Miroir TypeScript des DTOs Java du package com.stagehub.candidature. */

import { OffreResumeResponse } from './offre.models';

export type StatutCandidature = 'EN_ATTENTE' | 'ENTRETIEN' | 'ACCEPTEE' | 'REFUSEE';

/**
 * Copie de StatutCandidature.peutPasserA (backend) : sert uniquement à ne proposer que les actions possibles.
 * Le backend reste la référence et renvoie 409 si une transition est invalide.
 */
export const TRANSITIONS_CANDIDATURE: Record<StatutCandidature, StatutCandidature[]> = {
  EN_ATTENTE: ['ENTRETIEN', 'REFUSEE'],
  ENTRETIEN: ['ACCEPTEE', 'REFUSEE'],
  ACCEPTEE: [],
  REFUSEE: [],
};

export interface CandidatureEtudiantResponse {
  id: number;
  offre: OffreResumeResponse;
  lettreMotivation: string | null;
  statut: StatutCandidature;
  dateCandidature: string;
}

export interface CandidatureEntrepriseResponse {
  id: number;
  offreId: number;
  offreTitre: string;
  etudiantId: number;
  etudiantNom: string;
  etudiantPrenom: string;
  etudiantEmail: string;
  etudiantEcole: string;
  etudiantFiliere: string;
  lettreMotivation: string | null;
  statut: StatutCandidature;
  dateCandidature: string;
  scoreMatching: number | null;
}
