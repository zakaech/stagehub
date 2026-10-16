import { convertToParamMap } from '@angular/router';

import { filtreActif, lireFiltreOffres, memeTexte, paramsDeFiltreOffres } from './filtre-offres';

describe('filtre-offres', () => {
  it("lit ville, domaine et technologies (paramètre répété) depuis l'URL", () => {
    const filtre = lireFiltreOffres(
      convertToParamMap({ ville: ' Rabat ', domaine: 'Data', technologies: ['Java', ' SQL', ''] }),
    );

    expect(filtre).toEqual({ ville: 'Rabat', domaine: 'Data', technologies: ['Java', 'SQL'] });
    expect(filtreActif(filtre)).toBe(true);
  });

  it('un filtre vide ne génère aucun paramètre (null les retire de l’URL)', () => {
    const vide = lireFiltreOffres(convertToParamMap({ ville: '  ' }));

    expect(filtreActif(vide)).toBe(false);
    expect(paramsDeFiltreOffres(vide)).toEqual({ ville: null, domaine: null, technologies: null });
  });

  it("convertit un filtre en query params pour l'URL", () => {
    expect(
      paramsDeFiltreOffres({ ville: 'Rabat', domaine: null, technologies: ['Java', 'Python'] }),
    ).toEqual({ ville: 'Rabat', domaine: null, technologies: ['Java', 'Python'] });
  });

  it('compare les valeurs des listes déroulantes sans tenir compte de la casse', () => {
    expect(memeTexte('Rabat', 'rabat')).toBe(true);
    expect(memeTexte('Fès', 'FÈS')).toBe(true);
    expect(memeTexte('Rabat', 'Casablanca')).toBe(false);
    expect(memeTexte(null, null)).toBe(true);
  });
});
