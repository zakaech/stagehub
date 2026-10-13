import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { jwtDeTest } from '../../../testing/jwt-de-test';
import { AuthService, CLE_TOKEN, espaceDuRole } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  const creerService = () => TestBed.inject(AuthService);

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('démarre déconnecté sans jeton stocké', () => {
    const auth = creerService();

    expect(auth.estConnecte()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('connecter() stocke le jeton et expose la session déduite du JWT', () => {
    const auth = creerService();
    const token = jwtDeTest('ENTREPRISE', 3600, 'rh@acme.ma');

    auth.connecter({ email: 'rh@acme.ma', motDePasse: 'motdepasse1' }).subscribe();
    const requete = http.expectOne('/api/v1/auth/login');
    expect(requete.request.method).toBe('POST');
    requete.flush({ accessToken: token, tokenType: 'Bearer', expiresIn: 3600, role: 'ENTREPRISE' });

    expect(localStorage.getItem(CLE_TOKEN)).toBe(token);
    expect(auth.estConnecte()).toBe(true);
    expect(auth.role()).toBe('ENTREPRISE');
    expect(auth.session()?.email).toBe('rh@acme.ma');
    expect(auth.token()).toBe(token);
  });

  it('restaure une session valide depuis localStorage (rechargement de page)', () => {
    localStorage.setItem(CLE_TOKEN, jwtDeTest('ETUDIANT'));

    expect(creerService().role()).toBe('ETUDIANT');
  });

  it('ignore et supprime un jeton expiré', () => {
    localStorage.setItem(CLE_TOKEN, jwtDeTest('ETUDIANT', -10));

    const auth = creerService();

    expect(auth.estConnecte()).toBe(false);
    expect(localStorage.getItem(CLE_TOKEN)).toBeNull();
  });

  it('deconnecter() vide la session et renvoie vers /login', () => {
    localStorage.setItem(CLE_TOKEN, jwtDeTest('ADMIN'));
    const auth = creerService();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    auth.deconnecter();

    expect(auth.estConnecte()).toBe(false);
    expect(localStorage.getItem(CLE_TOKEN)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('associe à chaque rôle son espace', () => {
    expect(espaceDuRole('ETUDIANT')).toBe('/etudiant/candidatures');
    expect(espaceDuRole('ENTREPRISE')).toBe('/entreprise/offres');
    expect(espaceDuRole('ADMIN')).toBe('/admin/entreprises');
  });
});
