import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface LettreDialogData {
  candidat: string;
  lettre: string;
}

@Component({
  selector: 'app-lettre-dialog',
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Lettre de motivation — {{ data.candidat }}</h2>
    <mat-dialog-content>
      <p class="lettre">{{ data.lettre }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Fermer</button>
    </mat-dialog-actions>
  `,
  styles: `
    .lettre {
      white-space: pre-line;
    }
  `,
})
export class LettreDialog {
  protected readonly data = inject<LettreDialogData>(MAT_DIALOG_DATA);
}
