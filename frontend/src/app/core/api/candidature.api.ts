import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CandidatureEntrepriseResponse,
  CandidatureEtudiantResponse,
  StatutCandidature,
  TriCandidatures,
} from '../models/candidature.models';
import { PageRequest, PageResponse } from '../models/page.models';
import { API_URL } from './api';
import { paramsDePage } from './http-params';

@Injectable({ providedIn: 'root' })
export class CandidatureApi {
  private readonly http = inject(HttpClient);

  // --- Espace étudiant ---

  postuler(
    offreId: number,
    lettreMotivation: string | null,
  ): Observable<CandidatureEtudiantResponse> {
    return this.http.post<CandidatureEtudiantResponse>(
      `${API_URL}/etudiant/offres/${offreId}/candidatures`,
      { lettreMotivation },
    );
  }

  mesCandidatures(page: PageRequest): Observable<PageResponse<CandidatureEtudiantResponse>> {
    return this.http.get<PageResponse<CandidatureEtudiantResponse>>(
      `${API_URL}/etudiant/candidatures`,
      {
        params: paramsDePage(page),
      },
    );
  }

  // --- Espace entreprise ---

  /** Tri par date par défaut (paramètre absent) ; « score » : par score indicatif, sans score à la fin. */
  candidaturesDeLOffre(
    offreId: number,
    page: PageRequest,
    tri: TriCandidatures = 'date',
  ): Observable<PageResponse<CandidatureEntrepriseResponse>> {
    const params = paramsDePage(page);
    return this.http.get<PageResponse<CandidatureEntrepriseResponse>>(
      `${API_URL}/entreprise/offres/${offreId}/candidatures`,
      { params: tri === 'score' ? params.set('tri', 'score') : params },
    );
  }

  changerStatut(id: number, statut: StatutCandidature): Observable<CandidatureEntrepriseResponse> {
    return this.http.patch<CandidatureEntrepriseResponse>(
      `${API_URL}/entreprise/candidatures/${id}/statut`,
      {
        statut,
      },
    );
  }
}
