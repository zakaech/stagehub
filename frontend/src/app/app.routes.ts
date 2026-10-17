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
        loadComponent: () =>
          import('./features/offres/offre-list-page').then((m) => m.OffreListPage),
      },
      {
        path: 'offres/:id',
        title: 'Offre de stage — StageHub',
        loadComponent: () =>
          import('./features/offres/offre-detail-page').then((m) => m.OffreDetailPage),
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
        loadComponent: () =>
          import('./features/auth/inscription-page').then((m) => m.InscriptionPage),
      },
      {
        path: 'etudiant',
        canActivate: [roleGuard],
        data: { roles: ['ETUDIANT'] },
        children: [
          {
            path: 'candidatures',
            title: 'Mes candidatures — StageHub',
            loadComponent: () =>
              import('./features/etudiant/mes-candidatures-page').then(
                (m) => m.MesCandidaturesPage,
              ),
          },
          {
            path: 'profil',
            title: 'Mon profil — StageHub',
            loadComponent: () =>
              import('./features/etudiant/profil-page').then((m) => m.ProfilPage),
          },
        ],
      },
      {
        path: 'entreprise',
        canActivate: [roleGuard],
        data: { roles: ['ENTREPRISE'] },
        children: [
          {
            path: 'offres',
            title: 'Mes offres — StageHub',
            loadComponent: () =>
              import('./features/entreprise/mes-offres-page').then((m) => m.MesOffresPage),
          },
          {
            path: 'offres/nouvelle',
            title: 'Publier une offre — StageHub',
            loadComponent: () =>
              import('./features/entreprise/offre-form-page').then((m) => m.OffreFormPage),
          },
          {
            path: 'offres/:id/modifier',
            title: 'Modifier une offre — StageHub',
            loadComponent: () =>
              import('./features/entreprise/offre-form-page').then((m) => m.OffreFormPage),
          },
          {
            path: 'offres/:id/candidatures',
            title: 'Candidatures reçues — StageHub',
            loadComponent: () =>
              import('./features/entreprise/candidatures-offre-page').then(
                (m) => m.CandidaturesOffrePage,
              ),
          },
        ],
      },
      {
        path: 'admin',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        children: [
          {
            path: 'entreprises',
            title: 'Entreprises — StageHub',
            loadComponent: () =>
              import('./features/admin/entreprises-page').then((m) => m.EntreprisesPage),
          },
          {
            path: 'statistiques',
            title: 'Statistiques — StageHub',
            loadComponent: () =>
              import('./features/admin/statistiques-page').then((m) => m.StatistiquesPage),
          },
          {
            path: 'audit',
            title: "Journal d'audit — StageHub",
            loadComponent: () => import('./features/admin/audit-page').then((m) => m.AuditPage),
          },
        ],
      },
      { path: '**', title: 'Page introuvable — StageHub', component: PageIntrouvable },
    ],
  },
];
