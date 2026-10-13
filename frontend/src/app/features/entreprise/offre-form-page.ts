import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router, RouterLink } from '@angular/router';

import { OffreApi } from '../../core/api/offre.api';
import { appliquerErreursServeur } from '../../core/http/problem-detail';
import { OffreRequest } from '../../core/models/offre.models';
import { NotificationService } from '../../core/notification.service';
import { depuisDateIso, versDateIso } from '../../shared/dates';
import { messageErreurChamp } from '../../shared/form-errors';

/** Création (/entreprise/offres/nouvelle) et modification (/entreprise/offres/:id/modifier) d'une offre. */
@Component({
  selector: 'app-offre-form-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './offre-form-page.html',
  styleUrl: './offre-form-page.scss',
})
export class OffreFormPage {
  private readonly offreApi = inject(OffreApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Paramètre de route :id, absent en création. */
  readonly id = input<number | undefined, string | undefined>(undefined, {
    transform: (valeur) => (valeur ? Number(valeur) : undefined),
  });
  protected readonly modeEdition = computed(() => this.id() !== undefined);

  protected readonly form = this.fb.group({
    titre: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.maxLength(5000)]],
    ville: ['', [Validators.required, Validators.maxLength(100)]],
    domaine: ['', [Validators.required, Validators.maxLength(100)]],
    // Validators.required sur un tableau : invalide s'il est vide ; maxLength limite le nombre d'éléments
    technologies: this.fb.control<string[]>([], [Validators.required, Validators.maxLength(20)]),
    dureeMois: [6, [Validators.required, Validators.min(1), Validators.max(12)]],
    dateLimite: this.fb.control<Date | null>(null, Validators.required),
  });
  protected readonly aujourdhui = new Date();
  protected readonly enCours = signal(false);
  protected readonly erreur = messageErreurChamp;

  /** En édition, charge l'offre ; sans id, params renvoie undefined et la ressource reste inactive. */
  protected readonly offreExistante = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.offreApi.monOffre(id),
  });

  constructor() {
    effect(() => {
      if (this.offreExistante.hasValue()) {
        const offre = this.offreExistante.value();
        this.form.setValue({
          titre: offre.titre,
          description: offre.description,
          ville: offre.ville,
          domaine: offre.domaine,
          technologies: offre.technologies,
          dureeMois: offre.dureeMois,
          dateLimite: depuisDateIso(offre.dateLimite),
        });
      }
    });
  }

  protected ajouterTechnologie(event: MatChipInputEvent): void {
    const techno = event.value.trim();
    const actuelles = this.form.controls.technologies.value;
    if (
      techno &&
      techno.length <= 50 &&
      !actuelles.some((t) => t.toLowerCase() === techno.toLowerCase())
    ) {
      this.form.controls.technologies.setValue([...actuelles, techno]);
    }
    event.chipInput.clear();
  }

  protected retirerTechnologie(techno: string): void {
    const controle = this.form.controls.technologies;
    controle.setValue(controle.value.filter((t) => t !== techno));
    controle.markAsTouched();
  }

  protected messageTechnologies(): string {
    const erreurs = this.form.controls.technologies.errors;
    if (erreurs?.['required']) {
      return 'Ajoutez au moins une technologie (Entrée pour valider)';
    }
    if (erreurs?.['maxlength']) {
      return '20 technologies au maximum';
    }
    return this.erreur(this.form.controls.technologies);
  }

  protected enregistrer(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const valeurs = this.form.getRawValue();
    const offre: OffreRequest = { ...valeurs, dateLimite: versDateIso(valeurs.dateLimite!) };
    const id = this.id();

    this.enCours.set(true);
    const requete =
      id === undefined ? this.offreApi.creer(offre) : this.offreApi.modifier(id, offre);
    requete.subscribe({
      next: () => {
        this.notifications.succes(id === undefined ? 'Offre publiée' : 'Offre mise à jour');
        void this.router.navigate(['/entreprise/offres']);
      },
      error: (erreur: unknown) => {
        this.enCours.set(false);
        appliquerErreursServeur(this.form, erreur);
      },
    });
  }
}
