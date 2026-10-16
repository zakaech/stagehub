import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';

import { API_URL } from '../api/api';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notification.service';
import { messageErreur, problemDetail } from './problem-detail';

export const URL_LOGIN = `${API_URL}/auth/login`;

/**
 * Traitement global des erreurs HTTP. L'erreur est toujours relancée : le composant appelant
 * peut encore réagir (arrêter un indicateur de chargement, afficher un message sur un champ...).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  const traiter = (erreur: unknown): Observable<never> => {
    if (erreur instanceof HttpErrorResponse) {
      if (erreur.status === 401) {
        // 401 sur le login = identifiants incorrects : le formulaire l'affiche lui-même, pas de déconnexion
        if (req.url !== URL_LOGIN) {
          auth.deconnecter(false);
          notifications.erreur('Votre session a expiré, veuillez vous reconnecter');
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
        }
      } else if (!problemDetail(erreur)?.erreurs?.length) {
        // Les erreurs de validation (400 + "erreurs") sont affichées sous les champs par le formulaire
        notifications.erreur(messageErreur(erreur));
      }
    }
    return throwError(() => erreur);
  };

  return next(req).pipe(
    catchError((erreur: unknown) =>
      estProblemeDansUnBlob(erreur)
        ? from((erreur.error as Blob).text()).pipe(
            map((texte) => avecCorpsJson(erreur, texte)),
            switchMap(traiter),
          )
        : traiter(erreur),
    ),
  );
};

/**
 * Pour un téléchargement (responseType 'blob'), Angular livre aussi le corps d'erreur sous forme de Blob :
 * le ProblemDetail JSON doit être relu avant de pouvoir afficher son message.
 */
function estProblemeDansUnBlob(erreur: unknown): erreur is HttpErrorResponse & { error: Blob } {
  return (
    erreur instanceof HttpErrorResponse &&
    erreur.error instanceof Blob &&
    erreur.error.type.includes('json')
  );
}

function avecCorpsJson(erreur: HttpErrorResponse, texte: string): HttpErrorResponse {
  let corps: unknown = texte;
  try {
    corps = JSON.parse(texte);
  } catch {
    // Corps illisible : on garde le texte brut, messageErreur affichera un message générique
  }
  return new HttpErrorResponse({
    error: corps,
    headers: erreur.headers,
    status: erreur.status,
    statusText: erreur.statusText,
    url: erreur.url ?? undefined,
  });
}
