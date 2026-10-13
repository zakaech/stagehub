/** Miroir TypeScript des DTOs Java du package com.stagehub.auth (et des profils associés). */

export type Role = 'ETUDIANT' | 'ENTREPRISE' | 'ADMIN';

export type StatutValidation = 'EN_ATTENTE' | 'VALIDEE' | 'REFUSEE';

export interface LoginRequest {
  email: string;
  motDePasse: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  /** Durée de validité en secondes. */
  expiresIn: number;
  role: Role;
}

export interface RegisterEtudiantRequest {
  email: string;
  motDePasse: string;
  nom: string;
  prenom: string;
  ecole: string;
  filiere: string;
  ville: string;
}

export interface RegisterEntrepriseRequest {
  email: string;
  motDePasse: string;
  nom: string;
  secteur: string;
  ville: string;
  description: string | null;
}

export interface EtudiantProfil {
  id: number;
  nom: string;
  prenom: string;
  ecole: string;
  filiere: string;
  ville: string;
  cvDisponible: boolean;
}

export interface EntrepriseProfil {
  id: number;
  email: string;
  nom: string;
  secteur: string;
  ville: string;
  description: string | null;
  statutValidation: StatutValidation;
}

export interface ProfilResponse {
  id: number;
  email: string;
  role: Role;
  dateCreation: string;
  etudiant?: EtudiantProfil;
  entreprise?: EntrepriseProfil;
}
