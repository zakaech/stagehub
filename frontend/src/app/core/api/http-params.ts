import { HttpParams } from '@angular/common/http';

import { PageRequest } from '../models/page.models';

/** Convertit une demande de page en paramètres de requête Spring Data (?page=0&size=10&sort=...). */
export function paramsDePage(page: PageRequest): HttpParams {
  let params = new HttpParams().set('page', page.page).set('size', page.size);
  if (page.sort) {
    params = params.set('sort', page.sort);
  }
  return params;
}
