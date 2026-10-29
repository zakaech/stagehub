import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { messageErreurChamp } from '../../shared/form-errors';

/** Information de l'étudiant sur l'analyse de son CV (phase 8). Si l'IA passe à un service externe, l'adapter. */
export const INFORMATION_ANALYSE_IA =
  'Pour aider l’entreprise à lire les candidatures, votre CV sera analysé par un modèle d’IA hébergé par ' +
  'StageHub : il ne quitte pas nos serveurs. L’entreprise verra un score indicatif et un résumé. Aucune décision ' +
  'n’est prise automatiquement.';

export interface PostulerDialogData {
  titreOffre: string;
  /** null : inconnu (profil non chargé) — aucune mention n'est alors affichée. */
  cvDisponible: boolean | null;
}

/** Saisie de la lettre de motivation (facultative). Renvoie la lettre (ou null), undefined si annulé. */
@Component({
  selector: 'app-postuler-dialog',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Postuler : {{ data.titreOffre }}</h2>
    <mat-dialog-content>
      @if (data.cvDisponible === true) {
        <p class="cv">
          <mat-icon>attach_file</mat-icon> Votre CV actuel sera joint à la candidature.
        </p>
      } @else if (data.cvDisponible === false) {
        <p class="info">
          Vous n'avez pas encore déposé de CV : la candidature sera envoyée sans CV.
          <a routerLink="/etudiant/profil" mat-dialog-close>Déposer mon CV</a>
        </p>
      }
      <p class="info-ia" role="note">
        <mat-icon inline>info</mat-icon>
        <span>{{ informationIa }}</span>
      </p>
      <mat-form-field class="pleine-largeur">
        <mat-label>Lettre de motivation (facultative)</mat-label>
        <textarea matInput [formControl]="lettre" rows="8"></textarea>
        <mat-hint align="end">{{ lettre.value.length }} / 5000</mat-hint>
        <mat-error>{{ erreur(lettre) }}</mat-error>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-flat-button (click)="envoyer()" [disabled]="lettre.invalid">
        Envoyer ma candidature
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .pleine-largeur {
      width: 100%;
    }
    .cv {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .info-ia {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 8px 12px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    /* Sans cela, le texte (plusieurs lignes) écrase l'icône : seul un « ( » restait visible */
    .info-ia mat-icon {
      flex-shrink: 0;
    }
  `,
})
export class PostulerDialog {
  protected readonly data = inject<PostulerDialogData>(MAT_DIALOG_DATA);
  /** Texte validé par l'auteur (phase 8) : l'étudiant sait que son CV sera analysé, et comment. */
  protected readonly informationIa = INFORMATION_ANALYSE_IA;
  private readonly dialogRef = inject<MatDialogRef<PostulerDialog, string | null>>(MatDialogRef);

  protected readonly lettre = new FormControl('', {
    nonNullable: true,
    validators: Validators.maxLength(5000),
  });
  protected readonly erreur = messageErreurChamp;

  protected envoyer(): void {
    const texte = this.lettre.value.trim();
    this.dialogRef.close(texte.length > 0 ? texte : null);
  }
}
