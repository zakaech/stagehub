import { DatePipe } from '@angular/common';
import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AnalyseIa } from '../../core/models/candidature.models';

/** Motifs d'échec envoyés par ai-service, en clair pour l'entreprise. */
export const MOTIFS_ECHEC: Record<string, string> = {
  CV_ABSENT: 'pas de CV joint',
  CV_ILLISIBLE: 'CV illisible (PDF scanné, protégé ou abîmé)',
  OFFRE_INTROUVABLE: 'offre introuvable',
  ANALYSE_IMPOSSIBLE: 'service d’analyse indisponible',
};

/**
 * Cellule « Analyse IA » de la liste des candidatures : un affichage par état. Une fois l'analyse
 * terminée : score indicatif, comptage des technologies (« 3 sur 4 », calculé sans le modèle) et alertes, à côté
 * l'un de l'autre ; « Détails » ouvre la justification et le résumé.
 */
@Component({
  selector: 'app-analyse-ia-cell',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  template: `
    @let a = analyse();
    @if (!a) {
      <small class="discret" matTooltip="Candidature reçue avant la mise en place de l'analyse"
        >—</small
      >
    } @else {
      @switch (a.statut) {
        @case ('TERMINEE') {
          <div class="resultat">
            <span class="score" [attr.aria-label]="'Score indicatif ' + a.score + ' sur 100'">
              {{ a.score }}<small>/100</small>
            </span>
            <small class="indicatif">indicatif</small>
            @if (a.technologiesOffre) {
              <small
                class="techno"
                [matTooltip]="technologiesTooltip()"
                [attr.aria-label]="
                  'Technologies de l’offre retrouvées dans le CV : ' + technologies()
                "
              >
                <mat-icon inline>memory</mat-icon> {{ technologies() }}
              </small>
            }
            @if (a.alerteEcart) {
              <mat-icon
                class="alerte"
                matTooltip="Écart flagrant entre le score et les technologies retrouvées dans le CV : à vérifier"
                aria-label="Écart flagrant entre le score et les technologies : à vérifier"
                >compare_arrows</mat-icon
              >
            }
            @if (a.contenuSuspect) {
              <mat-icon
                class="alerte"
                matTooltip="Contenu inhabituel dans le CV (phrases adressées à l’IA ?) : à vérifier"
                aria-label="Contenu inhabituel dans le CV : à vérifier"
                >report</mat-icon
              >
            }
            <button mat-button class="details" (click)="details.emit()">Détails</button>
          </div>
        }
        @case ('EN_COURS') {
          <span class="etat">
            <mat-spinner diameter="16" aria-hidden="true" /> Analyse en cours…
          </span>
        }
        @case ('EN_ATTENTE') {
          <span class="etat">
            <mat-icon inline>hourglass_top</mat-icon>
            @if (a.prochaineTentative) {
              Nouvelle tentative à {{ a.prochaineTentative | date: 'HH:mm' }}
            } @else {
              En attente d’analyse
            }
          </span>
        }
        @case ('ECHEC') {
          <span class="etat echec">
            <mat-icon inline>error_outline</mat-icon> Analyse impossible : {{ motif() }}
          </span>
        }
        @case ('DESACTIVEE') {
          <span class="etat discret">
            <mat-icon inline>block</mat-icon> Analyse automatique désactivée
          </span>
        }
      }
    }
  `,
  styles: `
    .resultat {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px 8px;
    }
    .score {
      font: var(--mat-sys-title-medium);
    }
    .indicatif {
      font-style: italic;
      color: var(--mat-sys-on-surface-variant);
    }
    .techno {
      display: inline-flex;
      align-items: center;
      gap: 2px;
    }
    .alerte {
      color: var(--mat-sys-error);
    }
    .etat {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .echec {
      color: var(--mat-sys-error);
    }
    .discret {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class AnalyseIaCell {
  readonly analyse = input.required<AnalyseIa | null>();
  /** Demande d'affichage de la justification et du résumé. */
  readonly details = output<void>();

  protected readonly technologies = computed(() => {
    const a = this.analyse();
    return a ? `${a.technologiesTrouvees.length} sur ${a.technologiesOffre}` : '';
  });
  protected readonly technologiesTooltip = computed(() => {
    const trouvees = this.analyse()?.technologiesTrouvees ?? [];
    return trouvees.length
      ? `Retrouvées dans le CV : ${trouvees.join(', ')} (comptage automatique, sans l’IA)`
      : 'Aucune technologie de l’offre retrouvée dans le CV (comptage automatique, sans l’IA)';
  });
  protected readonly motif = computed(() => {
    const motif = this.analyse()?.motifEchec ?? '';
    return MOTIFS_ECHEC[motif] ?? 'raison inconnue';
  });
}
