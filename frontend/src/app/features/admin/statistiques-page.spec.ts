import { versBarres } from './statistiques-page';

describe('versBarres', () => {
  it('rend les barres proportionnelles à la plus grande valeur et met le % du total en infobulle', () => {
    const barres = versBarres(
      [
        { libelle: 'Java', valeur: 4 },
        { libelle: 'Python', valeur: 2 },
        { libelle: 'Go', valeur: 2 },
      ],
      'offre(s)',
    );

    expect(barres.map((b) => b.largeur)).toEqual([100, 50, 50]);
    expect(barres[0].infobulle).toBe('Java : 4 offre(s) (50 %)');
  });

  it('gère une série entièrement à zéro sans division par zéro', () => {
    const barres = versBarres([{ libelle: 'Refusées', valeur: 0 }], 'candidature(s)');

    expect(barres[0].largeur).toBe(0);
    expect(barres[0].infobulle).toBe('Refusées : 0 candidature(s)');
  });
});
