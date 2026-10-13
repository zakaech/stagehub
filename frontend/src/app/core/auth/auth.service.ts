import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { API_URL } from '../api/api';
import {
  LoginRequest,
  ProfilResponse,
  RegisterEntrepriseRequest,
  RegisterEtudiantRequest,
  Role,
  TokenResponse,
} from '../models/auth.models';
import { decoderJwt } from './jwt';

/** Session déduite du JWT : de quoi afficher l'interface sans appel réseau. */
export interface Session {
  token: string;
  email: string;
  role: Role;
  /** Expiration en millisecondes (epoch). */
  expireA: number;
}

export const CLE_TOKEN = 'stagehub.token';

/** Page d'accueil de chaque rôle : utilisée après le login et par le guestGuard. */
export function espaceDuRole(role: Role): string {
  switch (role) {
    case 'ETUDIANT':
      return '/etudiant/candidatures';
    case 'ENTREPRISE':
      return '/entreprise/offres';
    case 'ADMIN':
      return '/admin/entreprises';
  }
}

/**
 * Gère le jeton et l'état de connexion, exposés en signals.
 *
 * Le jeton est stocké dans localStorage pour survivre au rechargement de la page. Compromis assumé :
 * un script injecté (faille XSS) pourrait le lire. Un cookie httpOnly serait inaccessible au JavaScript,
 * mais le navigateur l'enverrait alors automatiquement, ce qui imposerait une protection CSRF côté backend.
 * Atténuations ici : Angular échappe les données affichées (pas de innerHTML), jeton court (1 h).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sessionCourante = signal<Session | null>(this.restaurerSession());
  private minuteurExpiration?: ReturnType<typeof setTimeout>;

  readonly session = this.sessionCourante.asReadonly();
  readonly estConnecte = computed(() => this.sessionCourante() !== null);
  readonly role = computed(() => this.sessionCourante()?.role ?? null);
  readonly profil = signal<ProfilResponse | null>(null);

  constructor() {
    this.programmerExpiration();
  }

  /** Jeton à envoyer, ou null s'il est absent ou expiré (la session est alors fermée). */
  token(): string | null {
    const session = this.sessionCourante();
    if (!session) {
      return null;
    }
    if (Date.now() >= session.expireA) {
      this.deconnecter(false);
      return null;
    }
    return session.token;
  }

  connecter(request: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${API_URL}/auth/login`, request)
      .pipe(tap((reponse) => this.ouvrirSession(reponse.accessToken)));
  }

  inscrireEtudiant(request: RegisterEtudiantRequest): Observable<ProfilResponse> {
    return this.http.post<ProfilResponse>(`${API_URL}/auth/register/etudiant`, request);
  }

  inscrireEntreprise(request: RegisterEntrepriseRequest): Observable<ProfilResponse> {
    return this.http.post<ProfilResponse>(`${API_URL}/auth/register/entreprise`, request);
  }

  chargerProfil(): Observable<ProfilResponse> {
    return this.http
      .get<ProfilResponse>(`${API_URL}/auth/me`)
      .pipe(tap((profil) => this.profil.set(profil)));
  }

  deconnecter(redirigerVersLogin = true): void {
    localStorage.removeItem(CLE_TOKEN);
    clearTimeout(this.minuteurExpiration);
    this.sessionCourante.set(null);
    this.profil.set(null);
    if (redirigerVersLogin) {
      void this.router.navigate(['/login']);
    }
  }

  private ouvrirSession(token: string): void {
    const session = sessionDepuisToken(token);
    if (!session) {
      throw new Error('Jeton reçu illisible');
    }
    localStorage.setItem(CLE_TOKEN, token);
    this.sessionCourante.set(session);
    this.profil.set(null);
    this.programmerExpiration();
  }

  private restaurerSession(): Session | null {
    const token = localStorage.getItem(CLE_TOKEN);
    const session = token ? sessionDepuisToken(token) : null;
    if (!session || Date.now() >= session.expireA) {
      localStorage.removeItem(CLE_TOKEN);
      return null;
    }
    return session;
  }

  /** Déconnexion automatique à l'expiration du jeton, même si l'utilisateur ne fait rien. */
  private programmerExpiration(): void {
    clearTimeout(this.minuteurExpiration);
    const session = this.sessionCourante();
    if (session) {
      this.minuteurExpiration = setTimeout(
        () => this.deconnecter(),
        Math.max(session.expireA - Date.now(), 0),
      );
    }
  }
}

function sessionDepuisToken(token: string): Session | null {
  const payload = decoderJwt(token);
  if (!payload?.role || !payload.exp) {
    return null;
  }
  return { token, email: payload.sub, role: payload.role, expireA: payload.exp * 1000 };
}
