import { Signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { PageEvent } from '@angular/material/paginator';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';

import { PageRequest } from '../core/models/page.models';

export interface OptionsPagination {
  tailleParDefaut: number;
  taillesAutorisees: number[];
}

/** Pagination des tableaux (candidatures, offres de l'entreprise, entreprises). */
export const PAGINATION_TABLEAU: OptionsPagination = {
  tailleParDefaut: 10,
  taillesAutorisees: [10, 25, 50],
};

/**
 * Lit la pagination dans les query params. Dans l'URL, la page commence à 1 (lisible : "?page=2"),
 * côté API à 0 (Spring Data). Une valeur absente, invalide ou non proposée revient à la valeur par défaut.
 */
export function lirePagination(params: ParamMap, options: OptionsPagination): PageRequest {
  const page = Number(params.get('page'));
  const taille = Number(params.get('size'));
  return {
    page: Number.isInteger(page) && page >= 1 ? page - 1 : 0,
    size: options.taillesAutorisees.includes(taille) ? taille : options.tailleParDefaut,
  };
}

/** Query params d'une page ; les valeurs par défaut sont retirées (null) pour garder des liens courts. */
export function paramsDePagination(event: PageEvent, options: OptionsPagination): Params {
  return {
    page: event.pageIndex > 0 ? event.pageIndex + 1 : null,
    size: event.pageSize !== options.tailleParDefaut ? event.pageSize : null,
  };
}

/**
 * État d'une liste paginée stocké dans l'URL : chaque changement de page ou de filtre est une navigation,
 * donc le bouton retour et le partage de lien restituent exactement la même vue.
 * À appeler dans un contexte d'injection (initialisation d'un champ de composant).
 */
export function etatListeDansUrl(options: OptionsPagination) {
  const route = inject(ActivatedRoute);
  const router = inject(Router);

  // queryParamMap émet immédiatement sa valeur courante : requireSync évite un premier état "undefined"
  const params: Signal<ParamMap> = toSignal(route.queryParamMap, { requireSync: true });
  const pagination = computed(() => lirePagination(params(), options));

  const naviguer = (changements: Params) =>
    void router.navigate([], {
      relativeTo: route,
      queryParams: changements,
      // On fusionne avec les paramètres existants ; une valeur null retire le paramètre de l'URL
      queryParamsHandling: 'merge',
    });

  return {
    params,
    pagination,
    changerPage: (event: PageEvent) => naviguer(paramsDePagination(event, options)),
    /** Un nouveau filtre ramène toujours à la première page. */
    filtrer: (filtres: Params) => naviguer({ ...filtres, page: null }),
  };
}
