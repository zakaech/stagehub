import { convertToParamMap } from '@angular/router';

import { lireFiltreEntite } from './audit-page';

describe("filtre du journal d'audit (URL)", () => {
  it('lit le type d’entité, tout le journal sinon', () => {
    expect(lireFiltreEntite(convertToParamMap({ entite: 'CANDIDATURE' }))).toBe('CANDIDATURE');
    expect(lireFiltreEntite(convertToParamMap({ entite: 'ENTREPRISE' }))).toBe('ENTREPRISE');
    expect(lireFiltreEntite(convertToParamMap({}))).toBeNull();
    expect(lireFiltreEntite(convertToParamMap({ entite: 'OFFRE' }))).toBeNull();
  });
});
