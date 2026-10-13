import { DatePipe } from '@angular/common';
import { Component, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';

import { CandidatureApi } from '../../core/api/candidature.api';
import { OffreApi } from '../../core/api/offre.api';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification.service';
import { PostulerDialog, PostulerDialogData } from './postuler-dialog';

@Component({
  selector: 'app-offre-detail-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './offre-detail-page.html',
  styleUrl: './offre-detail-page.scss',
})
export class OffreDetailPage {
  private readonly offreApi = inject(OffreApi);
  private readonly candidatureApi = inject(CandidatureApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  protected readonly auth = inject(AuthService);

  /** Paramètre de route :id, converti en nombre. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly offre = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.offreApi.detailPublic(id),
  });
  protected readonly candidatureEnvoyee = signal(false);
  protected readonly envoiEnCours = signal(false);

  protected postuler(titreOffre: string): void {
    this.dialog
      .open<PostulerDialog, PostulerDialogData, string | null>(PostulerDialog, {
        data: { titreOffre },
        width: '640px',
      })
      .afterClosed()
      .pipe(
        // undefined = fenêtre fermée sans envoyer
        filter((lettre): lettre is string | null => lettre !== undefined),
        switchMap((lettre) => {
          this.envoiEnCours.set(true);
          return this.candidatureApi.postuler(this.id(), lettre);
        }),
      )
      .subscribe({
        next: () => {
          this.envoiEnCours.set(false);
          this.candidatureEnvoyee.set(true);
          this.notifications.succes('Candidature envoyée !');
        },
        // Le message (ex. « Vous avez déjà postulé à cette offre ») est affiché par l'errorInterceptor
        error: () => this.envoiEnCours.set(false),
      });
  }
}
