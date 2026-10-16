import { ResourceRef, Signal, linkedSignal } from '@angular/core';

/**
 * Quand les paramètres d'une ressource changent (filtre, page), sa valeur repasse à undefined le temps
 * du chargement : la liste disparaîtrait puis réapparaîtrait. Ce signal garde la dernière valeur reçue
 * jusqu'à l'arrivée de la suivante (la barre de progression indique le chargement en cours).
 *
 * linkedSignal : signal dérivé d'une source, dont le calcul reçoit aussi sa valeur précédente.
 */
export function valeurConservee<T>(ressource: ResourceRef<T | undefined>): Signal<T | undefined> {
  return linkedSignal<T | undefined, T | undefined>({
    source: () => (ressource.hasValue() ? ressource.value() : undefined),
    computation: (valeur, precedent) => valeur ?? precedent?.value,
  });
}
