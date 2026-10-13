import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';

import { OffreApi } from '../../core/api/offre.api';
import { AuthService } from '../../core/auth/auth.service';
import { OffreResponse, StatutOffre } from '../../core/models/offre.models';
import { PageRequest } from '../../core/models/page.models';
import { NotificationService } from '../../core/notification.service';
import { confirmer } from '../../shared/confirm-dialog';
import { dateDepassee } from '../../shared/dates';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { StatutChip } from '../../shared/statut-chip';
import { StatutEntrepriseBanner } from './statut-entreprise-banner';

@Component({
  selector: 'app-mes-offres-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    StatutChip,
    StatutEntrepriseBanner,
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './mes-offres-page.html',
})
export class MesOffresPage {
  private readonly offreApi = inject(OffreApi);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly colonnes = ['titre', 'dateLimite', 'statut', 'actions'];
  protected readonly dateDepassee = dateDepassee;

  /** Le profil indique le statut de validation, qui conditionne la publication. */
  protected readonly profil = rxResource({ stream: () => this.auth.chargerProfil() });
  protected readonly statutValidation = computed(() =>
    this.profil.hasValue() ? (this.profil.value().entreprise?.statutValidation ?? null) : null,
  );

  protected readonly pagination = signal<PageRequest>({ page: 0, size: 10, sort: 'id,desc' });
  protected readonly offres = rxResource({
    params: () => this.pagination(),
    stream: ({ params }) => this.offreApi.mesOffres(params),
  });

  protected changerPage(event: PageEvent): void {
    this.pagination.update((p) => ({ ...p, page: event.pageIndex, size: event.pageSize }));
  }

  protected changerStatut(offre: OffreResponse, statut: StatutOffre): void {
    const fermer = statut === 'FERMEE';
    confirmer(this.dialog, {
      titre: fermer ? "Fermer l'offre ?" : "Rouvrir l'offre ?",
      message: fermer
        ? `« ${offre.titre} » ne sera plus visible et n'acceptera plus de candidatures.`
        : `« ${offre.titre} » sera de nouveau visible et ouverte aux candidatures.`,
      libelleConfirmer: fermer ? 'Fermer' : 'Rouvrir',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.offreApi.changerStatut(offre.id, statut)),
      )
      .subscribe(() => {
        this.notifications.succes(fermer ? 'Offre fermée' : 'Offre rouverte');
        this.offres.reload();
      });
  }
}
