import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { jwtDeTest } from '../../../testing/jwt-de-test';
import { authInterceptor } from './auth.interceptor';
import { CLE_TOKEN } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  const configurer = (token: string | null) => {
    localStorage.clear();
    if (token) {
      localStorage.setItem(CLE_TOKEN, token);
    }
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  };

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('ajoute le jeton Bearer aux appels vers /api/v1', () => {
    const token = jwtDeTest('ETUDIANT');
    configurer(token);

    http.get('/api/v1/etudiant/candidatures').subscribe();

    const requete = backend.expectOne('/api/v1/etudiant/candidatures');
    expect(requete.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    requete.flush({});
  });

  it("n'envoie jamais le jeton à un autre domaine", () => {
    configurer(jwtDeTest('ETUDIANT'));

    http.get('https://example.com/api/v1/x').subscribe();

    const requete = backend.expectOne('https://example.com/api/v1/x');
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({});
  });

  it("n'ajoute rien quand l'utilisateur n'est pas connecté", () => {
    configurer(null);

    http.get('/api/v1/offres').subscribe();

    const requete = backend.expectOne('/api/v1/offres');
    expect(requete.request.headers.has('Authorization')).toBe(false);
    requete.flush({});
  });
});
