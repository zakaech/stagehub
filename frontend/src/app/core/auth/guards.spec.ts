import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { jwtDeTest } from '../../../testing/jwt-de-test';
import { Role } from '../models/auth.models';
import { CLE_TOKEN } from './auth.service';
import { guestGuard, roleGuard } from './guards';

describe('guards', () => {
  const connecterEn = (role: Role | null) => {
    localStorage.clear();
    if (role) {
      localStorage.setItem(CLE_TOKEN, jwtDeTest(role));
    }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
  };

  const executerRoleGuard = (rolesAutorises: Role[], url = '/entreprise/offres') =>
    TestBed.runInInjectionContext(() =>
      roleGuard(
        { data: { roles: rolesAutorises } } as unknown as ActivatedRouteSnapshot,
        { url } as RouterStateSnapshot,
      ),
    );

  const executerGuestGuard = () =>
    TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, { url: '/login' } as RouterStateSnapshot),
    );

  const url = (resultat: unknown) => TestBed.inject(Router).serializeUrl(resultat as UrlTree);

  afterEach(() => localStorage.clear());

  describe('roleGuard', () => {
    it('laisse passer le bon rôle', () => {
      connecterEn('ENTREPRISE');
      expect(executerRoleGuard(['ENTREPRISE'])).toBe(true);
    });

    it('renvoie un visiteur vers /login en mémorisant la page demandée', () => {
      connecterEn(null);
      expect(url(executerRoleGuard(['ENTREPRISE']))).toBe('/login?returnUrl=%2Fentreprise%2Foffres');
    });

    it('renvoie un utilisateur du mauvais rôle vers son propre espace', () => {
      connecterEn('ETUDIANT');
      expect(url(executerRoleGuard(['ADMIN'], '/admin/statistiques'))).toBe('/etudiant/candidatures');
    });
  });

  describe('guestGuard', () => {
    it('laisse un visiteur accéder au login', () => {
      connecterEn(null);
      expect(executerGuestGuard()).toBe(true);
    });

    it('renvoie un utilisateur connecté vers son espace', () => {
      connecterEn('ADMIN');
      expect(url(executerGuestGuard())).toBe('/admin/entreprises');
    });
  });
});
