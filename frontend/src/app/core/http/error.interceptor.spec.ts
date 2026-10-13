import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notification.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const auth = { deconnecter: vi.fn() };
  const notifications = { erreur: vi.fn(), succes: vi.fn() };
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true) as never;
  });

  afterEach(() => backend.verify());

  /** Envoie une requête, y répond en erreur et renvoie le statut reçu par l'appelant. */
  const echouer = (url: string, status: number, corps: object = {}): number | undefined => {
    let statutRecu: number | undefined;
    http.post(url, {}).subscribe({ error: (e) => (statutRecu = e.status) });
    backend.expectOne(url).flush(corps, { status, statusText: 'Erreur' });
    return statutRecu;
  };

  it('401 sur /auth/login : ni déconnexion, ni redirection, ni snackbar (le formulaire affiche « identifiants incorrects »)', () => {
    const statut = echouer('/api/v1/auth/login', 401, { detail: 'Email ou mot de passe incorrect' });

    expect(statut).toBe(401);
    expect(auth.deconnecter).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(notifications.erreur).not.toHaveBeenCalled();
  });

  it('401 ailleurs : déconnexion, message et redirection vers /login', () => {
    echouer('/api/v1/etudiant/candidatures', 401);

    expect(auth.deconnecter).toHaveBeenCalledWith(false);
    expect(notifications.erreur).toHaveBeenCalledWith('Votre session a expiré, veuillez vous reconnecter');
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({ queryParams: expect.anything() }));
  });

  it('erreur métier : affiche le detail du ProblemDetail et relance l’erreur', () => {
    const statut = echouer('/api/v1/etudiant/offres/1/candidatures', 409, {
      status: 409,
      detail: 'Vous avez déjà postulé à cette offre',
      code: 'CandidatureDejaExistante',
    });

    expect(statut).toBe(409);
    expect(notifications.erreur).toHaveBeenCalledWith('Vous avez déjà postulé à cette offre');
    expect(auth.deconnecter).not.toHaveBeenCalled();
  });

  it('erreur de validation (400 + erreurs[]) : pas de snackbar, le formulaire affiche les champs', () => {
    echouer('/api/v1/auth/register/etudiant', 400, {
      status: 400,
      detail: 'La requête contient des champs invalides',
      erreurs: [{ champ: 'email', message: 'doit être une adresse électronique syntaxiquement correcte' }],
    });

    expect(notifications.erreur).not.toHaveBeenCalled();
  });
});
