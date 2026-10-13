import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import {
  DateAdapter,
  MAT_DATE_FORMATS,
  MAT_DATE_LOCALE,
  MAT_NATIVE_DATE_FORMATS,
} from '@angular/material/core';
import { MatIconRegistry } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { FrDateAdapter } from './shared/fr-date-adapter';
import { PaginatorFr } from './shared/paginator-fr';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding : les paramètres de route (:id) arrivent directement dans les input() des composants
    provideRouter(routes, withComponentInputBinding()),
    // Ordre important : authInterceptor ajoute le jeton, errorInterceptor traite la réponse en erreur
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // Dates, nombres et datepicker au format français
    { provide: LOCALE_ID, useValue: 'fr' },
    { provide: MAT_DATE_LOCALE, useValue: 'fr' },
    { provide: DateAdapter, useClass: FrDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: MAT_NATIVE_DATE_FORMATS },
    { provide: MatPaginatorIntl, useClass: PaginatorFr },
    // <mat-icon> utilise la police "Material Symbols Outlined" chargée dans index.html
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
    }),
  ],
};
