import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { Role } from '../core/models/auth.models';

interface LienNavigation {
  libelle: string;
  lien: string;
  icone: string;
}

const LIENS_PAR_ROLE: Record<Role, LienNavigation[]> = {
  ETUDIANT: [
    { libelle: 'Mes candidatures', lien: '/etudiant/candidatures', icone: 'assignment' },
    { libelle: 'Mon profil', lien: '/etudiant/profil', icone: 'badge' },
  ],
  ENTREPRISE: [{ libelle: 'Mes offres', lien: '/entreprise/offres', icone: 'work' }],
  ADMIN: [
    { libelle: 'Entreprises', lien: '/admin/entreprises', icone: 'domain_verification' },
    { libelle: 'Statistiques', lien: '/admin/statistiques', icone: 'bar_chart' },
  ],
};

/** Cadre commun à toutes les pages : barre de navigation adaptée au rôle, puis la page courante. */
@Component({
  selector: 'app-shell-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatToolbarModule,
  ],
  templateUrl: './shell-layout.html',
  styleUrl: './shell-layout.scss',
})
export class ShellLayout {
  protected readonly auth = inject(AuthService);

  protected readonly liens = computed<LienNavigation[]>(() => {
    const role = this.auth.role();
    const offres: LienNavigation = { libelle: 'Offres', lien: '/offres', icone: 'search' };
    return role ? [offres, ...LIENS_PAR_ROLE[role]] : [offres];
  });
}
