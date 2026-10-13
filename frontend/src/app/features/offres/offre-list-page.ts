import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { OffreApi } from '../../core/api/offre.api';
import { PageRequest } from '../../core/models/page.models';

/** Liste publique des offres ouvertes, paginée côté serveur (filtres en phase 3). */
@Component({
  selector: 'app-offre-list-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
  ],
  templateUrl: './offre-list-page.html',
  styleUrl: './offre-list-page.scss',
})
export class OffreListPage {
  private readonly offreApi = inject(OffreApi);

  protected readonly pagination = signal<PageRequest>({ page: 0, size: 9, sort: 'id,desc' });

  /**
   * rxResource relance la requête dès que le signal pagination change, et expose
   * l'état de chargement (isLoading), la valeur (value) et l'erreur (error) sous forme de signals.
   */
  protected readonly offres = rxResource({
    params: () => this.pagination(),
    stream: ({ params }) => this.offreApi.listerOuvertes(params),
  });

  protected changerPage(event: PageEvent): void {
    this.pagination.update((p) => ({ ...p, page: event.pageIndex, size: event.pageSize }));
  }
}
