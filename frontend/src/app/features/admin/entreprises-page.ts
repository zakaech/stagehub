import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleChange, MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ParamMap } from '@angular/router';
import { filter, switchMap } from 'rxjs';

import { AdminApi } from '../../core/api/admin.api';
import { EntrepriseProfil, StatutValidation } from '../../core/models/auth.models';
import { NotificationService } from '../../core/notification.service';
import { confirmer } from '../../shared/confirm-dialog';
import { PAGINATION_TABLEAU, etatListeDansUrl } from '../../shared/etat-url';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { StatutChip } from '../../shared/statut-chip';
import { valeurConservee } from '../../shared/valeur-conservee';

const STATUTS: StatutValidation[] = ['EN_ATTENTE', 'VALIDEE', 'REFUSEE'];

/** ?statut absent → EN_ATTENTE (vue par défaut) ; ?statut=TOUTES → toutes les entreprises (null). */
export function lireFiltreStatut(params: ParamMap): StatutValidation | null {
  const valeur = params.get('statut');
  if (valeur === 'TOUTES') {
    return null;
  }
  return STATUTS.find((s) => s === valeur) ?? 'EN_ATTENTE';
}

export function paramDeFiltreStatut(statut: StatutValidation | null): string | null {
  if (statut === null) {
    return 'TOUTES';
  }
  return statut === 'EN_ATTENTE' ? null : statut;
}

/** Validation des comptes entreprise (admin). Par défaut : les comptes en attente. */
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

  /** Filtre et page lus dans l'URL : ?statut=VALIDEE&page=2 (sans statut : les comptes en attente). */
  protected readonly etat = etatListeDansUrl(PAGINATION_TABLEAU);
  protected readonly filtre = computed(() => lireFiltreStatut(this.etat.params()));

  protected readonly entreprises = rxResource({
    params: () => ({ statut: this.filtre(), page: this.etat.pagination() }),
    stream: ({ params }) => this.adminApi.entreprises(params.statut, params.page),
  });
  /** Dernière page reçue, conservée pendant les rechargements (pas de clignotement). */
  protected readonly entreprisesAffichees = valeurConservee(this.entreprises);

  protected changerFiltre(event: MatButtonToggleChange): void {
    this.etat.filtrer({ statut: paramDeFiltreStatut(event.value as StatutValidation | null) });
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
