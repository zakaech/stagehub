import { convertToParamMap } from '@angular/router';

import { PAGINATION_TABLEAU, lirePagination, paramsDePagination } from './etat-url';

describe('etat-url', () => {
  const lire = (params: Record<string, string>) =>
    lirePagination(convertToParamMap(params), PAGINATION_TABLEAU);

  it("lit la page (1 dans l'URL = 0 pour l'API) et la taille", () => {
    expect(lire({ page: '3', size: '25' })).toEqual({ page: 2, size: 25 });
  });

  it('revient aux valeurs par défaut si les paramètres sont absents ou invalides', () => {
    expect(lire({})).toEqual({ page: 0, size: 10 });
    expect(lire({ page: '0', size: '7' })).toEqual({ page: 0, size: 10 });
    expect(lire({ page: '-2', size: 'abc' })).toEqual({ page: 0, size: 10 });
    expect(lire({ page: '1.5', size: '1000' })).toEqual({ page: 0, size: 10 });
  });

  it("retire de l'URL les valeurs par défaut (liens courts)", () => {
    expect(
      paramsDePagination({ pageIndex: 0, pageSize: 10, length: 50 }, PAGINATION_TABLEAU),
    ).toEqual({ page: null, size: null });
    expect(
      paramsDePagination({ pageIndex: 2, pageSize: 25, length: 50 }, PAGINATION_TABLEAU),
    ).toEqual({ page: 3, size: 25 });
  });
});
