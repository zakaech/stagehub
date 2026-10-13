import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Messages courts en bas de l'écran (snackbar Material). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  succes(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 3000 });
  }

  erreur(message: string): void {
    this.snackBar.open(message, 'Fermer', { duration: 6000, panelClass: 'snack-erreur' });
  }
}
