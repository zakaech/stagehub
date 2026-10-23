/** Miroir TypeScript des DTOs Java de l'administration. */

import { StatutCandidature } from './candidature.models';

export interface TechnologieStat {
  technologie: string;
  nombreOffres: number;
}

/** Partie « offres et entreprises » des statistiques (offre-service, phase 7 c). */
export interface StatistiquesOffresResponse {
  nombreOffres: number;
  nombreOffresOuvertes: number;
  topTechnologies: TechnologieStat[];
  entreprisesEnAttente: number;
}

/** Partie « candidatures » des statistiques (candidature-service). */
export interface StatistiquesCandidaturesResponse {
  nombreCandidatures: number;
  candidaturesParStatut: Record<StatutCandidature, number>;
}

/** Depuis la phase 7 (c), chaque service tient le journal de ses entités : le filtre est obligatoire. */
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
