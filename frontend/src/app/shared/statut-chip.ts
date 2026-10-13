import { Component, computed, input } from '@angular/core';

import { StatutValidation } from '../core/models/auth.models';
import { StatutCandidature } from '../core/models/candidature.models';
import { StatutOffre } from '../core/models/offre.models';

export type Statut = StatutCandidature | StatutOffre | StatutValidation;

const LIBELLES: Record<Statut, string> = {
  EN_ATTENTE: 'En attente',
  ENTRETIEN: 'Entretien',
  ACCEPTEE: 'Acceptée',
  REFUSEE: 'Refusée',
  OUVERTE: 'Ouverte',
  FERMEE: 'Fermée',
  VALIDEE: 'Validée',
};

/** Pastille colorée pour tous les statuts (candidature, offre, validation d'entreprise). */
@Component({
  selector: 'app-statut-chip',
  template: `<span [class]="'chip ' + ton()">{{ libelle() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font: var(--mat-sys-label-medium);
      white-space: nowrap;
    }
    .neutre {
      background: var(--mat-sys-surface-container-highest);
      color: var(--mat-sys-on-surface-variant);
    }
    .info {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .succes {
      background: #c8f0d0;
      color: #0b5a25;
    }
    .erreur {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
  `,
})
export class StatutChip {
  readonly statut = input.required<Statut>();

  protected readonly libelle = computed(() => LIBELLES[this.statut()]);
  protected readonly ton = computed(() => {
    switch (this.statut()) {
      case 'ENTRETIEN':
        return 'info';
      case 'ACCEPTEE':
      case 'VALIDEE':
      case 'OUVERTE':
        return 'succes';
      case 'REFUSEE':
        return 'erreur';
      default:
        return 'neutre';
    }
  });
}
