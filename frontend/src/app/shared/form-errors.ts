import { AbstractControl } from '@angular/forms';

/** Message à afficher dans <mat-error> pour un champ invalide (erreurs Angular ou renvoyées par le serveur). */
export function messageErreurChamp(control: AbstractControl | null): string {
  const erreurs = control?.errors;
  if (!erreurs) {
    return '';
  }
  if (erreurs['serveur']) {
    return erreurs['serveur'] as string;
  }
  if (erreurs['required']) {
    return 'Champ obligatoire';
  }
  if (erreurs['email']) {
    return 'Adresse email invalide';
  }
  if (erreurs['minlength']) {
    return `Au moins ${erreurs['minlength'].requiredLength} caractères`;
  }
  if (erreurs['maxlength']) {
    return `Au plus ${erreurs['maxlength'].requiredLength} caractères`;
  }
  if (erreurs['min']) {
    return `Valeur minimale : ${erreurs['min'].min}`;
  }
  if (erreurs['max']) {
    return `Valeur maximale : ${erreurs['max'].max}`;
  }
  if (erreurs['matDatepickerParse']) {
    return 'Date invalide';
  }
  if (erreurs['matDatepickerMin']) {
    return 'La date ne peut pas être dans le passé';
  }
  return 'Valeur invalide';
}
