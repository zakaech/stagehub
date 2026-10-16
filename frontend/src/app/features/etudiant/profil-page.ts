import { HttpEventType } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { CvApi } from '../../core/api/cv.api';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification.service';
import { enregistrerFichier } from '../../shared/telechargement';
import { verifierFichierCv } from './cv-fichier';

@Component({
  selector: 'app-profil-page',
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  templateUrl: './profil-page.html',
  styleUrl: './profil-page.scss',
})
export class ProfilPage {
  private readonly auth = inject(AuthService);
  private readonly cvApi = inject(CvApi);
  private readonly notifications = inject(NotificationService);

  protected readonly profil = rxResource({ stream: () => this.auth.chargerProfil() });
  /** Pourcentage envoyé pendant le dépôt ; null hors dépôt. */
  protected readonly progression = signal<number | null>(null);
  protected readonly erreurFichier = signal<string | null>(null);

  protected deposer(champ: HTMLInputElement): void {
    const fichier = champ.files?.[0];
    // Réinitialise le champ : choisir à nouveau le même fichier redéclenchera l'événement "change"
    champ.value = '';
    if (!fichier) {
      return;
    }
    const erreur = verifierFichierCv(fichier);
    this.erreurFichier.set(erreur);
    if (erreur) {
      return;
    }

    this.progression.set(0);
    this.cvApi.deposer(fichier).subscribe({
      next: (evenement) => {
        if (evenement.type === HttpEventType.UploadProgress && evenement.total) {
          this.progression.set(Math.round((100 * evenement.loaded) / evenement.total));
        } else if (evenement.type === HttpEventType.Response) {
          this.progression.set(null);
          this.notifications.succes('CV enregistré');
          this.profil.reload();
        }
      },
      // Le message du serveur (413, 415...) est affiché par l'errorInterceptor
      error: () => this.progression.set(null),
    });
  }

  protected telecharger(): void {
    this.cvApi.telechargerMonCv().subscribe((reponse) => enregistrerFichier(reponse, 'mon-cv.pdf'));
  }
}
