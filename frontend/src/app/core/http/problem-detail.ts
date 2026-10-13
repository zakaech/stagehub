import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';

/** Corps d'erreur renvoyé par le backend (RFC 9457 + champs "code" et "erreurs" de StageHub). */
export interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
  code?: string;
  erreurs?: { champ: string; message: string }[];
}

export function problemDetail(erreur: unknown): ProblemDetail | null {
  if (erreur instanceof HttpErrorResponse && erreur.error && typeof erreur.error === 'object') {
    return erreur.error as ProblemDetail;
  }
  return null;
}

/** Message lisible pour l'utilisateur, quel que soit le type d'erreur. */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse && erreur.status === 0) {
    return 'Serveur injoignable : vérifiez que le backend est démarré';
  }
  return problemDetail(erreur)?.detail ?? 'Une erreur inattendue est survenue';
}

/**
 * Reporte les erreurs de validation du serveur ("erreurs": [{champ, message}]) sur les champs du formulaire.
 * Retourne true si au moins une erreur a pu être rattachée à un champ.
 */
export function appliquerErreursServeur(form: FormGroup, erreur: unknown): boolean {
  const erreurs = problemDetail(erreur)?.erreurs ?? [];
  let rattachee = false;
  for (const { champ, message } of erreurs) {
    const control: AbstractControl | null = form.get(champ);
    if (control) {
      control.setErrors({ serveur: message });
      control.markAsTouched();
      rattachee = true;
    }
  }
  return rattachee;
}
