import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AdminApi } from '../../core/api/admin.api';
import { StatutCandidature } from '../../core/models/candidature.models';

/** Une ligne de graphique en barres horizontales : libellé, valeur, et largeur relative au maximum. */
export interface Barre {
  libelle: string;
  valeur: number;
  /** Largeur en % de la plus grande valeur de la série (0 à 100). */
  largeur: number;
  infobulle: string;
}

const LIBELLES_STATUT: Record<StatutCandidature, string> = {
  EN_ATTENTE: 'En attente',
  ENTRETIEN: 'Entretien',
  ACCEPTEE: 'Acceptées',
  REFUSEE: 'Refusées',
};

/** Les barres sont proportionnelles à la plus grande valeur ; le pourcentage du total va dans l'infobulle. */
export function versBarres(lignes: { libelle: string; valeur: number }[], unite: string): Barre[] {
  const max = Math.max(...lignes.map((l) => l.valeur), 0);
  const total = lignes.reduce((somme, l) => somme + l.valeur, 0);
  return lignes.map(({ libelle, valeur }) => ({
    libelle,
    valeur,
    largeur: max === 0 ? 0 : (valeur / max) * 100,
    infobulle: `${libelle} : ${valeur} ${unite}${total > 0 ? ` (${Math.round((valeur / total) * 100)} %)` : ''}`,
  }));
}

@Component({
  selector: 'app-statistiques-page',
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './statistiques-page.html',
  styleUrl: './statistiques-page.scss',
})
export class StatistiquesPage {
  private readonly adminApi = inject(AdminApi);

  protected readonly stats = rxResource({ stream: () => this.adminApi.statistiques() });

  protected readonly barresStatuts = computed<Barre[]>(() => {
    if (!this.stats.hasValue()) {
      return [];
    }
    const parStatut = this.stats.value().candidaturesParStatut;
    // Ordre du parcours d'une candidature, pas ordre de grandeur : il reste stable d'un chargement à l'autre
    const ordre: StatutCandidature[] = ['EN_ATTENTE', 'ENTRETIEN', 'ACCEPTEE', 'REFUSEE'];
    return versBarres(
      ordre.map((statut) => ({ libelle: LIBELLES_STATUT[statut], valeur: parStatut[statut] ?? 0 })),
      'candidature(s)',
    );
  });

  protected readonly barresTechnologies = computed<Barre[]>(() =>
    this.stats.hasValue()
      ? versBarres(
          this.stats.value().topTechnologies.map((t) => ({ libelle: t.technologie, valeur: t.nombreOffres })),
          'offre(s)',
        )
      : [],
  );
}
