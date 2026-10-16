import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import { OffreApi } from '../../core/api/offre.api';
import { OffreFiltre } from '../../core/models/offre.models';
import { etatListeDansUrl } from '../../shared/etat-url';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { valeurConservee } from '../../shared/valeur-conservee';
import { filtreActif, lireFiltreOffres, memeTexte, paramsDeFiltreOffres } from './filtre-offres';

/** Recherche publique des offres ouvertes : filtres et page vivent dans l'URL (?ville=…&page=2). */
@Component({
  selector: 'app-offre-list-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './offre-list-page.html',
  styleUrl: './offre-list-page.scss',
})
export class OffreListPage {
  private readonly offreApi = inject(OffreApi);

  protected readonly etat = etatListeDansUrl({
    tailleParDefaut: 9,
    taillesAutorisees: [9, 18, 36],
  });
  protected readonly filtre = computed(() => lireFiltreOffres(this.etat.params()));
  protected readonly filtreActif = computed(() => filtreActif(this.filtre()));
  protected readonly memeTexte = memeTexte;

  /** Valeurs des listes déroulantes, chargées une fois. */
  protected readonly filtresDisponibles = rxResource({
    stream: () => this.offreApi.filtresDisponibles(),
  });

  /**
   * rxResource relance la recherche dès que ses paramètres changent, c'est-à-dire dès que l'URL change
   * (filtre, page, bouton retour) ; il expose isLoading, value et error sous forme de signals.
   */
  protected readonly offres = rxResource({
    params: () => ({ filtre: this.filtre(), page: { ...this.etat.pagination(), sort: 'id,desc' } }),
    stream: ({ params }) => this.offreApi.rechercherOuvertes(params.filtre, params.page),
  });
  /** Dernière page reçue, conservée pendant les rechargements (pas de clignotement). */
  protected readonly offresAffichees = valeurConservee(this.offres);

  protected modifierFiltre(changement: Partial<OffreFiltre>): void {
    this.etat.filtrer(paramsDeFiltreOffres({ ...this.filtre(), ...changement }));
  }

  protected reinitialiser(): void {
    this.etat.filtrer(paramsDeFiltreOffres({ ville: null, domaine: null, technologies: [] }));
  }
}
