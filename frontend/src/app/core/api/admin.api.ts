import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { StatistiquesResponse } from '../models/admin.models';
import { EntrepriseProfil, StatutValidation } from '../models/auth.models';
import { PageRequest, PageResponse } from '../models/page.models';
import { API_URL } from './api';
import { paramsDePage } from './http-params';

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  /** statut null = toutes les entreprises. */
  entreprises(
    statut: StatutValidation | null,
    page: PageRequest,
  ): Observable<PageResponse<EntrepriseProfil>> {
    let params: HttpParams = paramsDePage(page);
    if (statut) {
      params = params.set('statut', statut);
    }
    return this.http.get<PageResponse<EntrepriseProfil>>(`${API_URL}/admin/entreprises`, {
      params,
    });
  }

  statuer(entrepriseId: number, decision: StatutValidation): Observable<EntrepriseProfil> {
    return this.http.patch<EntrepriseProfil>(
      `${API_URL}/admin/entreprises/${entrepriseId}/validation`,
      {
        decision,
      },
    );
  }

  statistiques(): Observable<StatistiquesResponse> {
    return this.http.get<StatistiquesResponse>(`${API_URL}/admin/stats`);
  }
}
