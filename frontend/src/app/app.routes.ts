import { Routes } from '@angular/router';

import { guestGuard } from './core/auth/guards';
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
      { path: '', pathMatch: 'full', redirectTo: 'login' },
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
      { path: '**', title: 'Page introuvable — StageHub', component: PageIntrouvable },
    ],
  },
];
