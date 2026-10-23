import { convertToParamMap } from '@angular/router';

import { lireFiltreEntite, paramDeFiltreEntite } from './audit-page';

describe("filtre du journal d'audit (URL)", () => {
  it('lit le type d’entité, les candidatures par défaut (plus de vue « tout le journal »)', () => {
    expect(lireFiltreEntite(convertToParamMap({ entite: 'CANDIDATURE' }))).toBe('CANDIDATURE');
    expect(lireFiltreEntite(convertToParamMap({ entite: 'ENTREPRISE' }))).toBe('ENTREPRISE');
    expect(lireFiltreEntite(convertToParamMap({}))).toBe('CANDIDATURE');
    expect(lireFiltreEntite(convertToParamMap({ entite: 'OFFRE' }))).toBe('CANDIDATURE');
  });

  it("n'écrit pas la vue par défaut dans l'URL", () => {
    expect(paramDeFiltreEntite('CANDIDATURE')).toBeNull();
    expect(paramDeFiltreEntite('ENTREPRISE')).toBe('ENTREPRISE');
  });
});
