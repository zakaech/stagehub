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
  /** Analyse par l'IA (phase 8), indicative ; null pour une candidature antérieure à l'analyse. */
  analyse: AnalyseIa | null;
  /** CV figé au moment de la candidature, téléchargeable par l'entreprise. */
  cvDisponible: boolean;
}

/**
 * État de l'analyse par l'IA. DESACTIVEE : en attente alors que l'IA est arrêtée (calculé par le serveur).
 * EN_ATTENTE peut porter une nouvelle tentative programmée après une panne (prochaineTentative).
 */
export type StatutAnalyse = 'EN_ATTENTE' | 'EN_COURS' | 'TERMINEE' | 'ECHEC' | 'DESACTIVEE';

/** Fournisseur qui analyse les CV (spec §1) : avec OpenAI, le CV quitte les serveurs de StageHub. */
export type FournisseurIa = 'OLLAMA' | 'OPENAI';

/** Miroir de InformationAnalyseResponse (GET /etudiant/analyse-ia). */
export interface InformationAnalyseResponse {
  fournisseur: FournisseurIa;
}

/** Miroir de AnalyseIaResponse (candidature-service) : vu par l'entreprise seulement. */
export interface AnalyseIa {
  statut: StatutAnalyse;
  /** CV_ABSENT, CV_ILLISIBLE, OFFRE_INTROUVABLE, ANALYSE_IMPOSSIBLE (ECHEC seulement). */
  motifEchec: string | null;
  prochaineTentative: string | null;
  /** Score indicatif de 0 à 100 (TERMINEE seulement). */
  score: number | null;
  justification: string | null;
  resume: string[];
  /** Technologies de l'offre retrouvées dans le CV : comptage déterministe, sans le modèle. */
  technologiesTrouvees: string[];
  technologiesOffre: number | null;
  /** Écart flagrant entre le score du modèle et la part des technologies retrouvées. */
  alerteEcart: boolean;
  /** Phrases qui ressemblent à des consignes adressées au modèle (heuristique : à vérifier). */
  contenuSuspect: boolean;
  modele: string | null;
  date: string | null;
}

/** Ordre des candidatures d'une offre : par date (défaut) ou par score indicatif, au choix de l'entreprise. */
export type TriCandidatures = 'date' | 'score';
