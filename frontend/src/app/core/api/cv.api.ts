import { HttpClient, HttpEvent, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EtudiantProfil } from '../models/auth.models';
import { API_URL } from './api';

@Injectable({ providedIn: 'root' })
export class CvApi {
  private readonly http = inject(HttpClient);

  /**
   * Envoi multipart (FormData, champ "fichier"). observe: 'events' + reportProgress : on reçoit
   * les événements de progression de l'envoi, puis la réponse finale.
   */
  deposer(fichier: File): Observable<HttpEvent<EtudiantProfil>> {
    const formulaire = new FormData();
    formulaire.append('fichier', fichier);
    return this.http.post<EtudiantProfil>(`${API_URL}/etudiant/cv`, formulaire, {
      reportProgress: true,
      observe: 'events',
    });
  }

  /** Réponse complète (en-têtes compris) pour lire le nom de fichier dans Content-Disposition. */
  telechargerMonCv(): Observable<HttpResponse<Blob>> {
    return this.http.get(`${API_URL}/etudiant/cv`, { responseType: 'blob', observe: 'response' });
  }

  telechargerCvCandidature(candidatureId: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${API_URL}/entreprise/candidatures/${candidatureId}/cv`, {
      responseType: 'blob',
      observe: 'response',
    });
  }
}
