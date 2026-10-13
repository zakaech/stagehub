import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

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

  return next(req).pipe(
    catchError((erreur: unknown) => {
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
    }),
  );
};
