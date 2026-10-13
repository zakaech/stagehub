import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../models/auth.models';
import { AuthService, espaceDuRole } from './auth.service';

/**
 * Protège un espace par rôle (route.data.roles). Non connecté → /login (avec retour prévu) ;
 * mauvais rôle → son propre espace. Confort d'interface uniquement : le backend refuse de toute façon (401/403).
 */
export const roleGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const rolesAutorises = (route.data['roles'] ?? []) as Role[];
  const role = auth.token() ? auth.role() : null;

  if (!role) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  return rolesAutorises.includes(role) ? true : router.createUrlTree([espaceDuRole(role)]);
};

/** Pages réservées aux visiteurs (login, inscription) : un utilisateur connecté est renvoyé vers son espace. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = auth.token() ? auth.role() : null;
  return role ? inject(Router).createUrlTree([espaceDuRole(role)]) : true;
};
