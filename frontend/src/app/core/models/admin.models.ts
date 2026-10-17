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

export type EntiteAuditee = 'CANDIDATURE' | 'ENTREPRISE';
export type ActionAudit = 'CHANGEMENT_STATUT_CANDIDATURE' | 'VALIDATION_ENTREPRISE';

/** Miroir de com.stagehub.audit.dto.AuditLogResponse. */
export interface AuditLogResponse {
  id: number;
  action: ActionAudit;
  utilisateur: string;
  entite: EntiteAuditee;
  entiteId: number;
  ancienneValeur: string | null;
  nouvelleValeur: string | null;
  date: string;
}
