import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { AnalyseIa } from '../../core/models/candidature.models';

/** Avertissement validé par l'auteur (phase 8) : affiché au-dessus des scores et dans le détail d'une analyse. */
export const AVERTISSEMENT_SCORE =
  'Calculé automatiquement par un modèle d’IA à partir du CV et de l’offre, il peut se tromper et ne remplace pas la ' +
  'lecture du CV. Aucune décision n’est prise automatiquement : c’est vous qui choisissez.';

export interface AnalyseIaDialogData {
  candidat: string;
  analyse: AnalyseIa;
}

/** Détail d'une analyse terminée : justification, résumé du CV, technologies retrouvées, alertes. */
@Component({
  selector: 'app-analyse-ia-dialog',
  imports: [DatePipe, MatButtonModule, MatDialogModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>Analyse indicative : {{ data.candidat }}</h2>
    <mat-dialog-content>
      <p class="avertissement" role="note">
        <mat-icon inline>info</mat-icon> <strong>Score indicatif.</strong> {{ avertissement }}
      </p>
      <p class="score">
        <strong>{{ data.analyse.score }}/100</strong>
        @if (data.analyse.technologiesOffre) {
          · technologies de l’offre retrouvées dans le CV :
          {{ data.analyse.technologiesTrouvees.length }} sur {{ data.analyse.technologiesOffre }}
          @if (data.analyse.technologiesTrouvees.length) {
            ({{ data.analyse.technologiesTrouvees.join(', ') }})
          }
        }
      </p>
      @if (data.analyse.alerteEcart) {
        <p class="alerte">
          <mat-icon inline>compare_arrows</mat-icon> Écart flagrant entre le score de l’IA et les
          technologies retrouvées dans le CV : à vérifier en lisant le CV.
        </p>
      }
      @if (data.analyse.contenuSuspect) {
        <p class="alerte">
          <mat-icon inline>report</mat-icon> Le CV contient des phrases qui ressemblent à des
          consignes adressées à l’IA : le score peut en avoir été influencé, à vérifier en lisant le
          CV.
        </p>
      }
      <h3>Justification</h3>
      <p>{{ data.analyse.justification }}</p>
      <h3>Résumé du CV</h3>
      <ul>
        @for (ligne of data.analyse.resume; track $index) {
          <li>{{ ligne }}</li>
        }
      </ul>
      <p class="source">
        Modèle {{ data.analyse.modele }}, analyse du {{ data.analyse.date | date: 'd MMM y' }} à
        {{ data.analyse.date | date: 'HH:mm' }}.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Fermer</button>
    </mat-dialog-actions>
  `,
  styles: `
    .avertissement {
      padding: 8px 12px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    .alerte {
      color: var(--mat-sys-error);
    }
    .source {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class AnalyseIaDialog {
  protected readonly data = inject<AnalyseIaDialogData>(MAT_DIALOG_DATA);
  protected readonly avertissement = AVERTISSEMENT_SCORE;
}
