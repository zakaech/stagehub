import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { ConnexionPage } from './connexion-page';

describe('ConnexionPage', () => {
  const auth = { connecter: vi.fn() };
  let navigateByUrl: ReturnType<typeof vi.fn>;

  const creer = async (returnUrl?: string) => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ConnexionPage],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    navigateByUrl = vi
      .spyOn(TestBed.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true) as never;

    const fixture = TestBed.createComponent(ConnexionPage);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    await fixture.whenStable();
    return fixture;
  };

  const remplirEtSoumettre = async (fixture: Awaited<ReturnType<typeof creer>>) => {
    const element = fixture.nativeElement as HTMLElement;
    const saisir = (selecteur: string, valeur: string) => {
      const input = element.querySelector<HTMLInputElement>(selecteur)!;
      input.value = valeur;
      input.dispatchEvent(new Event('input'));
    };
    saisir('input[formControlName="email"]', 'sara@etu.ma');
    saisir('input[formControlName="motDePasse"]', 'motdepasse1');
    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
    return element;
  };

  it('affiche « Identifiants incorrects » sur un 401 et reste sur la page', async () => {
    auth.connecter.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = await creer();

    const element = await remplirEtSoumettre(fixture);

    expect(element.textContent).toContain('Identifiants incorrects');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it("redirige vers l'espace du rôle après connexion", async () => {
    auth.connecter.mockReturnValue(of({ role: 'ENTREPRISE' }));
    const fixture = await creer();

    await remplirEtSoumettre(fixture);

    expect(auth.connecter).toHaveBeenCalledWith({
      email: 'sara@etu.ma',
      motDePasse: 'motdepasse1',
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/entreprise/offres');
  });

  it('revient sur la page demandée avant le login (returnUrl interne)', async () => {
    auth.connecter.mockReturnValue(of({ role: 'ETUDIANT' }));
    const fixture = await creer('/offres/3');

    await remplirEtSoumettre(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/offres/3');
  });

  it('ignore un returnUrl externe (redirection ouverte)', async () => {
    auth.connecter.mockReturnValue(of({ role: 'ETUDIANT' }));
    const fixture = await creer('//site-malveillant.example');

    await remplirEtSoumettre(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/etudiant/candidatures');
  });
});
