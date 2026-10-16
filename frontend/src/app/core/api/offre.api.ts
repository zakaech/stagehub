import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  FiltresDisponibles,
  OffreFiltre,
  OffreRequest,
  OffreResponse,
  StatutOffre,
} from '../models/offre.models';
import { PageRequest, PageResponse } from '../models/page.models';
import { API_URL } from './api';
import { paramsDePage } from './http-params';

@Injectable({ providedIn: 'root' })
export class OffreApi {
  private readonly http = inject(HttpClient);

  // --- Consultation publique ---

  rechercherOuvertes(
    filtre: OffreFiltre,
    page: PageRequest,
  ): Observable<PageResponse<OffreResponse>> {
    let params = paramsDePage(page);
    if (filtre.ville) {
      params = params.set('ville', filtre.ville);
    }
    if (filtre.domaine) {
      params = params.set('domaine', filtre.domaine);
    }
    // Paramètre répété : ?technologies=Java&technologies=SQL
    for (const technologie of filtre.technologies) {
      params = params.append('technologies', technologie);
    }
    return this.http.get<PageResponse<OffreResponse>>(`${API_URL}/offres`, { params });
  }

  filtresDisponibles(): Observable<FiltresDisponibles> {
    return this.http.get<FiltresDisponibles>(`${API_URL}/offres/filtres`);
  }

  detailPublic(id: number): Observable<OffreResponse> {
    return this.http.get<OffreResponse>(`${API_URL}/offres/${id}`);
  }

  // --- Espace entreprise ---

  mesOffres(page: PageRequest): Observable<PageResponse<OffreResponse>> {
    return this.http.get<PageResponse<OffreResponse>>(`${API_URL}/entreprise/offres`, {
      params: paramsDePage(page),
    });
  }

  monOffre(id: number): Observable<OffreResponse> {
    return this.http.get<OffreResponse>(`${API_URL}/entreprise/offres/${id}`);
  }

  creer(offre: OffreRequest): Observable<OffreResponse> {
    return this.http.post<OffreResponse>(`${API_URL}/entreprise/offres`, offre);
  }

  modifier(id: number, offre: OffreRequest): Observable<OffreResponse> {
    return this.http.put<OffreResponse>(`${API_URL}/entreprise/offres/${id}`, offre);
  }

  changerStatut(id: number, statut: StatutOffre): Observable<OffreResponse> {
    return this.http.patch<OffreResponse>(`${API_URL}/entreprise/offres/${id}/statut`, { statut });
  }
}
