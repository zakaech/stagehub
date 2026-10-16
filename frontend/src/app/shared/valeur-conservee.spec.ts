import { ResourceRef, signal } from '@angular/core';

import { valeurConservee } from './valeur-conservee';

describe('valeurConservee', () => {
  it('garde la dernière valeur pendant un rechargement, puis prend la nouvelle', () => {
    // Ressource simulée : seuls hasValue() et value() sont utilisés
    const valeur = signal<string | undefined>(undefined);
    const ressource = {
      hasValue: () => valeur() !== undefined,
      value: valeur,
    } as unknown as ResourceRef<string | undefined>;
    const affichee = valeurConservee(ressource);

    expect(affichee()).toBeUndefined();

    valeur.set('page 1');
    expect(affichee()).toBe('page 1');

    valeur.set(undefined); // nouveau filtre : chargement en cours
    expect(affichee()).toBe('page 1');

    valeur.set('page filtrée');
    expect(affichee()).toBe('page filtrée');
  });
});
