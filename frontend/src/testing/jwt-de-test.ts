import { Role } from '../app/core/models/auth.models';

/** Fabrique un JWT non signé (signature factice) : le frontend ne fait que lire le payload. */
export function jwtDeTest(role: Role, expireDansSecondes = 3600, email = 'test@stagehub.ma'): string {
  const base64Url = (objet: object) =>
    btoa(JSON.stringify(objet)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const exp = Math.floor(Date.now() / 1000) + expireDansSecondes;
  return `${base64Url({ alg: 'HS256' })}.${base64Url({ sub: email, uid: 1, role, exp })}.signature`;
}
