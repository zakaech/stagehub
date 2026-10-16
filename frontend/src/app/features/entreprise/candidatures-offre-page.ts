import { DatePipe } from '@angular/common';
import { Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { EMPTY, of, switchMap } from 'rxjs';

import { CandidatureApi } from '../../core/api/candidature.api';
import { CvApi } from '../../core/api/cv.api';
import { OffreApi } from '../../core/api/offre.api';
import {
  CandidatureEntrepriseResponse,
  StatutCandidature,
  TRANSITIONS_CANDIDATURE,
} from '../../core/models/candidature.models';
import { NotificationService } from '../../core/notification.service';
import { confirmer } from '../../shared/confirm-dialog';
import { PAGINATION_TABLEAU, etatListeDansUrl } from '../../shared/etat-url';
import { PAGINATOR_FR } from '../../shared/paginator-fr';
import { StatutChip } from '../../shared/statut-chip';
import { enregistrerFichier } from '../../shared/telechargement';
import { valeurConservee } from '../../shared/valeur-conservee';
import { LettreDialog, LettreDialogData } from './lettre-dialog';

const ACTIONS: Record<StatutCandidature, { libelle: string; icone: string }> = {
  EN_ATTENTE: { libelle: 'Remettre en attente', icone: 'hourglass_top' },
  ENTRETIEN: { libelle: 'Proposer un entretien', icone: 'event' },
  ACCEPTEE: { libelle: 'Accepter', icone: 'check_circle' },
  REFUSEE: { libelle: 'Refuser', icone: 'cancel' },
};

@Component({
  selector: 'app-candidatures-offre-page',
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
  ],
  providers: [PAGINATOR_FR],
  templateUrl: './candidatures-offre-page.html',
})
export class CandidaturesOffrePage {
  private readonly offreApi = inject(OffreApi);
  private readonly candidatureApi = inject(CandidatureApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly cvApi = inject(CvApi);

  /** Paramètre de route :id (identifiant de l'offre). */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly colonnes = [
    'candidat',
    'formation',
    'date',
    'lettre',
    'cv',
    'statut',
    'actions',
  ];

  protected readonly offre = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.offreApi.monOffre(id),
  });
  /** Page courante lue dans l'URL (?page=2&size=25). */
  protected readonly etat = etatListeDansUrl(PAGINATION_TABLEAU);
  protected readonly candidatures = rxResource({
    params: () => ({ offreId: this.id(), page: this.etat.pagination() }),
    stream: ({ params }) => this.candidatureApi.candidaturesDeLOffre(params.offreId, params.page),
  });
  /** Dernière page reçue, conservée pendant les rechargements (pas de clignotement). */
  protected readonly candidaturesAffichees = valeurConservee(this.candidatures);

  /** Statuts atteignables depuis le statut actuel (vide pour ACCEPTEE et REFUSEE). */
  protected transitionsDe(c: CandidatureEntrepriseResponse): StatutCandidature[] {
    return TRANSITIONS_CANDIDATURE[c.statut];
  }

  protected action(statut: StatutCandidature): { libelle: string; icone: string } {
    return ACTIONS[statut];
  }

  /** CV joint au moment de la candidature (figé côté serveur), même si l'étudiant l'a remplacé depuis. */
  protected telechargerCv(c: CandidatureEntrepriseResponse): void {
    this.cvApi
      .telechargerCvCandidature(c.id)
      .subscribe((reponse) =>
        enregistrerFichier(reponse, `CV ${c.etudiantPrenom} ${c.etudiantNom}.pdf`),
      );
  }

  protected voirLettre(c: CandidatureEntrepriseResponse): void {
    this.dialog.open<LettreDialog, LettreDialogData>(LettreDialog, {
      data: { candidat: `${c.etudiantPrenom} ${c.etudiantNom}`, lettre: c.lettreMotivation ?? '' },
      width: '640px',
    });
  }

  /** ACCEPTEE et REFUSEE sont définitifs : on demande confirmation avant de les appliquer. */
  protected changerStatut(c: CandidatureEntrepriseResponse, statut: StatutCandidature): void {
    const definitif = statut === 'ACCEPTEE' || statut === 'REFUSEE';
    const confirmation = definitif
      ? confirmer(this.dialog, {
          titre: `${ACTIONS[statut].libelle} la candidature ?`,
          message: `Candidature de ${c.etudiantPrenom} ${c.etudiantNom}. Cette décision est définitive.`,
          libelleConfirmer: ACTIONS[statut].libelle,
        })
      : of(true);

    confirmation
      .pipe(switchMap((ok) => (ok ? this.candidatureApi.changerStatut(c.id, statut) : EMPTY)))
      .subscribe(() => {
        this.notifications.succes('Statut de la candidature mis à jour');
        this.candidatures.reload();
      });
  }
}
