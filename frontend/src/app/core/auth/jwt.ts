import { Role } from '../models/auth.models';

/** Claims utiles du JWT StageHub (voir JwtService côté backend). */
export interface JwtPayload {
  sub: string;
  uid: number;
  role: Role;
  /** Expiration, en secondes depuis l'epoch. */
  exp: number;
}

/**
 * Lit la partie centrale du JWT (base64url → JSON). Aucune vérification de signature ici :
 * le frontend ne fait que lire des informations d'affichage ; c'est le backend qui vérifie le jeton.
 */
export function decoderJwt(token: string): JwtPayload | null {
  const morceaux = token.split('.');
  if (morceaux.length !== 3) {
    return null;
  }
  try {
    const base64 = morceaux[1].replace(/-/g, '+').replace(/_/g, '/');
    const octets = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(octets)) as JwtPayload;
  } catch {
    return null;
  }
}
