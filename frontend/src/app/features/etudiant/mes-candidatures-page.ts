import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { CandidatureApi } from '../../core/api/candidature.api';
import { PageRequest } from '../../core/models/page.models';
import { StatutChip } from '../../shared/statut-chip';

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
  protected readonly pagination = signal<PageRequest>({ page: 0, size: 10 });
  protected readonly candidatures = rxResource({
    params: () => this.pagination(),
    stream: ({ params }) => this.candidatureApi.mesCandidatures(params),
  });

  protected changerPage(event: PageEvent): void {
    this.pagination.set({ page: event.pageIndex, size: event.pageSize });
  }
}
