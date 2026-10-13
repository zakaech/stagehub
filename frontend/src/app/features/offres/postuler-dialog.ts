import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { messageErreurChamp } from '../../shared/form-errors';

export interface PostulerDialogData {
  titreOffre: string;
}

/** Saisie de la lettre de motivation (facultative). Renvoie la lettre (ou null), undefined si annulé. */
@Component({
  selector: 'app-postuler-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Postuler : {{ data.titreOffre }}</h2>
    <mat-dialog-content>
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
  `,
})
export class PostulerDialog {
  protected readonly data = inject<PostulerDialogData>(MAT_DIALOG_DATA);
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
