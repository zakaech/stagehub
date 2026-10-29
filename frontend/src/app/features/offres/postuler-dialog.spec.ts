import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';

import { INFORMATION_ANALYSE_IA, PostulerDialog } from './postuler-dialog';

describe('PostulerDialog', () => {
  it('informe l’étudiant que son CV sera analysé par une IA, sans décision automatique', async () => {
    await TestBed.configureTestingModule({
      imports: [PostulerDialog],
      providers: [
        provideRouter([]),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { titreOffre: 'Ingénieur DevOps', cvDisponible: true },
        },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PostulerDialog);
    await fixture.whenStable();

    const texte = (fixture.nativeElement as HTMLElement).textContent!;
    expect(texte).toContain(INFORMATION_ANALYSE_IA);
    expect(INFORMATION_ANALYSE_IA).toContain('Aucune décision n’est prise automatiquement');
  });
});
