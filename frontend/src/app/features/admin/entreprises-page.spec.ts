import { convertToParamMap } from '@angular/router';

import { lireFiltreStatut, paramDeFiltreStatut } from './entreprises-page';

describe('filtre de statut des entreprises (URL)', () => {
  it('sans paramètre : les comptes en attente', () => {
    expect(lireFiltreStatut(convertToParamMap({}))).toBe('EN_ATTENTE');
    expect(paramDeFiltreStatut('EN_ATTENTE')).toBeNull();
  });

  it('?statut=TOUTES : toutes les entreprises (null pour l’API)', () => {
    expect(lireFiltreStatut(convertToParamMap({ statut: 'TOUTES' }))).toBeNull();
    expect(paramDeFiltreStatut(null)).toBe('TOUTES');
  });

  it('statut explicite, valeur inconnue ramenée à la vue par défaut', () => {
    expect(lireFiltreStatut(convertToParamMap({ statut: 'VALIDEE' }))).toBe('VALIDEE');
    expect(paramDeFiltreStatut('REFUSEE')).toBe('REFUSEE');
    expect(lireFiltreStatut(convertToParamMap({ statut: 'N_IMPORTE_QUOI' }))).toBe('EN_ATTENTE');
  });
});
