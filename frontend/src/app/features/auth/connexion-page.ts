import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { AuthService, espaceDuRole } from '../../core/auth/auth.service';
import { appliquerErreursServeur } from '../../core/http/problem-detail';
import { messageErreurChamp } from '../../shared/form-errors';

@Component({
  selector: 'app-connexion-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './connexion-page.html',
  styleUrl: './auth-page.scss',
})
export class ConnexionPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Page demandée avant d'être renvoyé au login (query param, lié grâce à withComponentInputBinding). */
  readonly returnUrl = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', Validators.required],
  });
  protected readonly enCours = signal(false);
  protected readonly identifiantsIncorrects = signal(false);
  protected readonly motDePasseVisible = signal(false);
  protected readonly erreur = messageErreurChamp;

  protected soumettre(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enCours.set(true);
    this.identifiantsIncorrects.set(false);

    this.auth.connecter(this.form.getRawValue()).subscribe({
      next: (reponse) => void this.router.navigateByUrl(this.destination(reponse.role)),
      error: (erreur: unknown) => {
        this.enCours.set(false);
        if (erreur instanceof HttpErrorResponse && erreur.status === 401) {
          this.identifiantsIncorrects.set(true);
        } else {
          appliquerErreursServeur(this.form, erreur);
        }
      },
    });
  }

  /** N'accepte qu'un chemin interne ("/..."), jamais une URL externe (redirection ouverte). */
  private destination(role: Parameters<typeof espaceDuRole>[0]): string {
    const retour = this.returnUrl();
    return retour?.startsWith('/') && !retour.startsWith('//') ? retour : espaceDuRole(role);
  }
}
