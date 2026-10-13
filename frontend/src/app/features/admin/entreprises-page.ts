import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleChange, MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { filter, switchMap } from 'rxjs';

import { AdminApi } from '../../core/api/admin.api';
import { EntrepriseProfil, StatutValidation } from '../../core/models/auth.models';
import { NotificationService } from '../../core/notification.service';
import { confirmer } from '../../shared/confirm-dialog';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { StatutChip } from '../../shared/statut-chip';

/** Validation des comptes entreprise (spec §2, admin). Par défaut : les comptes en attente. */
@Component({
  selector: 'app-entreprises-page',
  imports: [
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    StatutChip,
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './entreprises-page.html',
})
export class EntreprisesPage {
  private readonly adminApi = inject(AdminApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly colonnes = ['nom', 'contact', 'secteur', 'statut', 'actions'];
  protected readonly filtre = signal<StatutValidation | null>('EN_ATTENTE');
  protected readonly pagination = signal({ page: 0, size: 10 });

  protected readonly entreprises = rxResource({
    params: () => ({ statut: this.filtre(), page: this.pagination() }),
    stream: ({ params }) => this.adminApi.entreprises(params.statut, params.page),
  });

  protected changerFiltre(event: MatButtonToggleChange): void {
    this.filtre.set(event.value as StatutValidation | null);
    this.pagination.update((p) => ({ ...p, page: 0 }));
  }

  protected changerPage(event: PageEvent): void {
    this.pagination.set({ page: event.pageIndex, size: event.pageSize });
  }

  protected statuer(entreprise: EntrepriseProfil, decision: 'VALIDEE' | 'REFUSEE'): void {
    const valider = decision === 'VALIDEE';
    confirmer(this.dialog, {
      titre: valider ? `Valider ${entreprise.nom} ?` : `Refuser ${entreprise.nom} ?`,
      message: valider
        ? "L'entreprise pourra publier des offres. Cette décision est définitive."
        : "L'entreprise ne pourra pas publier d'offres. Cette décision est définitive.",
      libelleConfirmer: valider ? 'Valider' : 'Refuser',
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.adminApi.statuer(entreprise.id, decision)),
      )
      .subscribe(() => {
        this.notifications.succes(
          valider ? `${entreprise.nom} est validée` : `${entreprise.nom} est refusée`,
        );
        this.entreprises.reload();
      });
  }
}
