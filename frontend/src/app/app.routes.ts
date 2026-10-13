import { Routes } from '@angular/router';

import { guestGuard, roleGuard } from './core/auth/guards';
import { ShellLayout } from './layout/shell-layout';
import { PageIntrouvable } from './shared/page-introuvable';

/**
 * Toutes les pages s'affichent dans le ShellLayout (barre de navigation).
 * Les pages sont chargées à la demande (loadComponent) : chaque espace a son propre fichier JS.
 */
export const routes: Routes = [
  {
    path: '',
    component: ShellLayout,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'offres' },
      {
        path: 'offres',
        title: 'Offres de stage — StageHub',
        loadComponent: () => import('./features/offres/offre-list-page').then((m) => m.OffreListPage),
      },
      {
        path: 'offres/:id',
        title: 'Offre de stage — StageHub',
        loadComponent: () => import('./features/offres/offre-detail-page').then((m) => m.OffreDetailPage),
      },
      {
        path: 'login',
        title: 'Connexion — StageHub',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/connexion-page').then((m) => m.ConnexionPage),
      },
      {
        path: 'inscription',
        title: 'Inscription — StageHub',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/inscription-page').then((m) => m.InscriptionPage),
      },
      {
        path: 'etudiant/candidatures',
        title: 'Mes candidatures — StageHub',
        canActivate: [roleGuard],
        data: { roles: ['ETUDIANT'] },
        loadComponent: () =>
          import('./features/etudiant/mes-candidatures-page').then((m) => m.MesCandidaturesPage),
      },
      { path: '**', title: 'Page introuvable — StageHub', component: PageIntrouvable },
    ],
  },
];
