import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

const FORMAT_FR = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*$/;

/**
 * L'adaptateur natif s'appuie sur Date.parse, qui lit "05/11/2026" comme le 11 mai (format américain)
 * et rejette "28/11/2026". Celui-ci lit explicitement la saisie au format français jj/mm/aaaa.
 */
@Injectable()
export class FrDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const morceaux = FORMAT_FR.exec(value);
      if (morceaux) {
        const [jour, mois, annee] = morceaux.slice(1).map(Number);
        const date = new Date(annee, mois - 1, jour);
        // new Date(2026, 1, 31) donne le 3 mars : on refuse les dates qui « débordent »
        return date.getDate() === jour && date.getMonth() === mois - 1 ? date : this.invalid();
      }
    }
    return super.parse(value, parseFormat);
  }
}
