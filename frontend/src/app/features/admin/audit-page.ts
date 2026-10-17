import { DatePipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonToggleChange, MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ParamMap } from '@angular/router';

import { AdminApi } from '../../core/api/admin.api';
import { ActionAudit, EntiteAuditee } from '../../core/models/admin.models';
import { etatListeDansUrl } from '../../shared/etat-url';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { Statut, StatutChip } from '../../shared/statut-chip';
import { valeurConservee } from '../../shared/valeur-conservee';

const ENTITES: EntiteAuditee[] = ['CANDIDATURE', 'ENTREPRISE'];

/** ?entite absent ou inconnu → tout le journal. */
export function lireFiltreEntite(params: ParamMap): EntiteAuditee | null {
  return ENTITES.find((e) => e === params.get('entite')) ?? null;
}

const LIBELLES_ACTION: Record<ActionAudit, string> = {
  CHANGEMENT_STATUT_CANDIDATURE: 'Statut de candidature',
  VALIDATION_ENTREPRISE: "Validation d'entreprise",
};

/** Journal d'audit (admin) : qui a changé quel statut, quand, et de quelle valeur à quelle valeur. */
@Component({
  selector: 'app-audit-page',
  imports: [
    DatePipe,
    MatButtonToggleModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    StatutChip,
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './audit-page.html',
  styles: `
    .changement {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  `,
})
export class AuditPage {
  private readonly adminApi = inject(AdminApi);

  protected readonly colonnes = ['date', 'action', 'entite', 'utilisateur', 'changement'];
  protected readonly etat = etatListeDansUrl({
    tailleParDefaut: 25,
    taillesAutorisees: [25, 50, 100],
  });
  protected readonly filtre = computed(() => lireFiltreEntite(this.etat.params()));

  protected readonly journal = rxResource({
    params: () => ({ entite: this.filtre(), page: this.etat.pagination() }),
    stream: ({ params }) => this.adminApi.journalAudit(params.entite, params.page),
  });
  protected readonly journalAffiche = valeurConservee(this.journal);

  protected libelleAction(action: ActionAudit): string {
    return LIBELLES_ACTION[action];
  }

  /** Les valeurs auditées sont des statuts (candidature ou validation), affichés avec la pastille commune. */
  protected statut(valeur: string): Statut {
    return valeur as Statut;
  }

  protected changerFiltre(event: MatButtonToggleChange): void {
    this.etat.filtrer({ entite: event.value as EntiteAuditee | null });
  }
}
