import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CandidatureApi } from './candidature.api';

describe('CandidatureApi.candidaturesDeLOffre', () => {
  let api: CandidatureApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CandidatureApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('trie par date par défaut : aucun paramètre de tri envoyé', () => {
    api.candidaturesDeLOffre(3, { page: 0, size: 10 }).subscribe();

    const requete = http.expectOne((r) => r.url === '/api/v1/entreprise/offres/3/candidatures');
    expect(requete.request.params.has('tri')).toBe(false);
    requete.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('tri par score indicatif sur demande', () => {
    api.candidaturesDeLOffre(3, { page: 1, size: 25 }, 'score').subscribe();

    const requete = http.expectOne((r) => r.url === '/api/v1/entreprise/offres/3/candidatures');
    expect(requete.request.params.get('tri')).toBe('score');
    expect(requete.request.params.get('page')).toBe('1');
    requete.flush({ content: [], page: 1, size: 25, totalElements: 0, totalPages: 0 });
  });
});
