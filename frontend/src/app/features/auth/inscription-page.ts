import { Component, inject, signal } from '@angular/core';
import { FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';

import { AuthService, espaceDuRole } from '../../core/auth/auth.service';
import { appliquerErreursServeur, problemDetail } from '../../core/http/problem-detail';
import { ProfilResponse } from '../../core/models/auth.models';
import { NotificationService } from '../../core/notification.service';
import { messageErreurChamp } from '../../shared/form-errors';

/** Mêmes bornes que le backend : 8 caractères minimum, 72 maximum (limite de BCrypt). */
const MOT_DE_PASSE = [Validators.required, Validators.minLength(8), Validators.maxLength(72)];

@Component({
  selector: 'app-inscription-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTabsModule,
  ],
  templateUrl: './inscription-page.html',
  styleUrl: './auth-page.scss',
})
export class InscriptionPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly formEtudiant = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    motDePasse: ['', MOT_DE_PASSE],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    prenom: ['', [Validators.required, Validators.maxLength(100)]],
    ecole: ['', [Validators.required, Validators.maxLength(150)]],
    filiere: ['', [Validators.required, Validators.maxLength(150)]],
    ville: ['', [Validators.required, Validators.maxLength(100)]],
  });

  protected readonly formEntreprise = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    motDePasse: ['', MOT_DE_PASSE],
    nom: ['', [Validators.required, Validators.maxLength(150)]],
    secteur: ['', [Validators.required, Validators.maxLength(100)]],
    ville: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(2000)],
  });

  protected readonly enCours = signal(false);
  protected readonly erreur = messageErreurChamp;

  protected inscrireEtudiant(): void {
    this.inscrire(this.formEtudiant, () =>
      this.auth.inscrireEtudiant(this.formEtudiant.getRawValue()),
    );
  }

  protected inscrireEntreprise(): void {
    const valeurs = this.formEntreprise.getRawValue();
    this.inscrire(this.formEntreprise, () =>
      this.auth.inscrireEntreprise({ ...valeurs, description: valeurs.description || null }),
    );
  }

  /** Crée le compte puis connecte directement l'utilisateur avec les mêmes identifiants. */
  private inscrire(form: FormGroup, creerCompte: () => Observable<ProfilResponse>): void {
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    this.enCours.set(true);
    const { email, motDePasse } = form.getRawValue() as { email: string; motDePasse: string };

    creerCompte()
      .pipe(switchMap(() => this.auth.connecter({ email, motDePasse })))
      .subscribe({
        next: (reponse) => {
          this.notifications.succes(
            reponse.role === 'ENTREPRISE'
              ? 'Compte créé : il doit maintenant être validé par un administrateur'
              : 'Compte créé, bienvenue sur StageHub !',
          );
          void this.router.navigateByUrl(espaceDuRole(reponse.role));
        },
        error: (erreur: unknown) => {
          this.enCours.set(false);
          if (problemDetail(erreur)?.code === 'EmailDejaUtilise') {
            form.get('email')?.setErrors({ serveur: 'Un compte existe déjà avec cet email' });
          } else {
            appliquerErreursServeur(form, erreur);
          }
        },
      });
  }
}
