import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { CandidatureApi } from '../../core/api/candidature.api';
import { PAGINATION_TABLEAU, etatListeDansUrl } from '../../shared/etat-url';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { StatutChip } from '../../shared/statut-chip';
import { valeurConservee } from '../../shared/valeur-conservee';

@Component({
  selector: 'app-mes-candidatures-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    StatutChip,
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './mes-candidatures-page.html',
  styles: `
    .ferme {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class MesCandidaturesPage {
  private readonly candidatureApi = inject(CandidatureApi);

  protected readonly colonnes = ['offre', 'entreprise', 'date', 'statut'];
  /** Page courante lue dans l'URL (?page=2&size=25). */
  protected readonly etat = etatListeDansUrl(PAGINATION_TABLEAU);
  protected readonly candidatures = rxResource({
    params: () => this.etat.pagination(),
    stream: ({ params }) => this.candidatureApi.mesCandidatures(params),
  });
  /** Dernière page reçue, conservée pendant les rechargements (pas de clignotement). */
  protected readonly candidaturesAffichees = valeurConservee(this.candidatures);
}
