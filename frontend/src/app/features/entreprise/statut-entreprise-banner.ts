import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { StatutValidation } from '../../core/models/auth.models';

/** Explique à une entreprise non validée pourquoi elle ne peut pas encore publier (spec §4.1). */
@Component({
  selector: 'app-statut-entreprise-banner',
  imports: [MatIconModule],
  template: `
    @switch (statut()) {
      @case ('EN_ATTENTE') {
        <p class="info" role="status">
          <mat-icon inline>hourglass_top</mat-icon>
          Votre compte est en attente de validation par un administrateur. Vous pourrez publier des offres
          dès qu'il sera validé.
        </p>
      }
      @case ('REFUSEE') {
        <p class="message-erreur" role="status">
          <mat-icon>block</mat-icon>
          Votre compte entreprise a été refusé par l'administrateur : la publication d'offres n'est pas possible.
        </p>
      }
    }
  `,
})
export class StatutEntrepriseBanner {
  readonly statut = input.required<StatutValidation>();
}
