import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';

import {
  INFORMATION_ANALYSE_IA_LOCALE,
  INFORMATION_ANALYSE_IA_OPENAI,
  PostulerDialog,
} from './postuler-dialog';

describe('PostulerDialog : information sur l’analyse du CV', () => {
  let http: HttpTestingController;

  const ouvrir = async () => {
    await TestBed.configureTestingModule({
      imports: [PostulerDialog],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { titreOffre: 'Ingénieur DevOps', cvDisponible: true },
        },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PostulerDialog);
    fixture.detectChanges();
    return fixture;
  };

  const texte = (element: HTMLElement) => element.textContent!.replace(/\s+/g, ' ');
  const boutonEnvoyer = (element: HTMLElement) =>
    [...element.querySelectorAll('button')].find((b) => b.textContent!.includes('Envoyer'))!;

  afterEach(() => http.verify());

  it('modèle local (Ollama) : le CV ne quitte pas les serveurs de StageHub', async () => {
    const fixture = await ouvrir();
    http.expectOne('/api/v1/etudiant/analyse-ia').flush({ fournisseur: 'OLLAMA' });
    await fixture.whenStable();

    expect(texte(fixture.nativeElement)).toContain(INFORMATION_ANALYSE_IA_LOCALE);
    expect(INFORMATION_ANALYSE_IA_LOCALE).toContain('Aucune décision n’est prise automatiquement');
  });

  it('OpenAI : le CV est envoyé à OpenAI, nom compris', async () => {
    const fixture = await ouvrir();
    http.expectOne('/api/v1/etudiant/analyse-ia').flush({ fournisseur: 'OPENAI' });
    await fixture.whenStable();

    expect(texte(fixture.nativeElement)).toContain(INFORMATION_ANALYSE_IA_OPENAI);
    expect(texte(fixture.nativeElement)).not.toContain('il ne quitte pas nos serveurs');
    expect(INFORMATION_ANALYSE_IA_OPENAI).toContain('il sera envoyé à OpenAI');
    expect(INFORMATION_ANALYSE_IA_OPENAI).toContain('le reste du CV, dont votre nom, est transmis');
  });

  it('fournisseur inconnu (erreur) : texte d’OpenAI, le plus prudent', async () => {
    const fixture = await ouvrir();
    http
      .expectOne('/api/v1/etudiant/analyse-ia')
      .flush({ detail: 'Service indisponible' }, { status: 503, statusText: 'Erreur' });
    await fixture.whenStable();

    expect(texte(fixture.nativeElement)).toContain(INFORMATION_ANALYSE_IA_OPENAI);
  });

  it('envoi impossible tant que l’information n’est pas affichée', async () => {
    const fixture = await ouvrir();
    expect(boutonEnvoyer(fixture.nativeElement).disabled).toBe(true);

    http.expectOne('/api/v1/etudiant/analyse-ia').flush({ fournisseur: 'OLLAMA' });
    await fixture.whenStable();
    expect(boutonEnvoyer(fixture.nativeElement).disabled).toBe(false);
  });
});
