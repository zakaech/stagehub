import { nomDepuisContentDisposition } from './telechargement';

describe('nomDepuisContentDisposition', () => {
  it('privilégie filename* (UTF-8, accents compris)', () => {
    expect(
      nomDepuisContentDisposition(
        `attachment; filename="CV Hel=?UTF-8?Q?ene?=.pdf"; filename*=UTF-8''CV%20H%C3%A9l%C3%A8ne%20Tazi.pdf`,
        'cv.pdf',
      ),
    ).toBe('CV Hélène Tazi.pdf');
  });

  it('se rabat sur filename, puis sur le nom par défaut', () => {
    expect(nomDepuisContentDisposition('attachment; filename="CV Sara.pdf"', 'cv.pdf')).toBe(
      'CV Sara.pdf',
    );
    expect(nomDepuisContentDisposition(null, 'cv.pdf')).toBe('cv.pdf');
    expect(nomDepuisContentDisposition('attachment', 'cv.pdf')).toBe('cv.pdf');
  });

  it('ignore un filename* mal encodé', () => {
    expect(
      nomDepuisContentDisposition(
        `attachment; filename="secours.pdf"; filename*=UTF-8''%E0%A4%A`,
        'cv.pdf',
      ),
    ).toBe('secours.pdf');
  });
});
