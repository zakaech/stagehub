import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-page-introuvable',
  imports: [RouterLink, MatButtonModule, MatIconModule],
  template: `
    <section class="etat-vide">
      <mat-icon>travel_explore</mat-icon>
      <h1>Page introuvable</h1>
      <p>Cette adresse ne correspond à aucune page de StageHub.</p>
      <a mat-flat-button routerLink="/">Retour à l'accueil</a>
    </section>
  `,
})
export class PageIntrouvable {}
