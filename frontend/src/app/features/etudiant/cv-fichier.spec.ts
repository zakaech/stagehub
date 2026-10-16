import { TAILLE_MAX_CV, verifierFichierCv } from './cv-fichier';

describe('verifierFichierCv', () => {
  const fichier = (nom: string, type: string, taille: number) => {
    const f = new File(['x'], nom, { type });
    // Évite d'allouer plusieurs Mo en mémoire pour tester la limite
    Object.defineProperty(f, 'size', { value: taille });
    return f;
  };

  it('accepte un PDF (par son type ou son extension)', () => {
    expect(verifierFichierCv(fichier('cv.pdf', 'application/pdf', 1000))).toBeNull();
    expect(verifierFichierCv(fichier('CV.PDF', '', 1000))).toBeNull();
    expect(verifierFichierCv(fichier('cv', 'application/pdf', TAILLE_MAX_CV))).toBeNull();
  });

  it('refuse un fichier vide, non PDF ou de plus de 5 Mo', () => {
    expect(verifierFichierCv(fichier('cv.pdf', 'application/pdf', 0))).toBe('Le fichier est vide');
    expect(verifierFichierCv(fichier('photo.png', 'image/png', 1000))).toBe(
      'Seuls les fichiers PDF sont acceptés',
    );
    expect(verifierFichierCv(fichier('cv.pdf', 'application/pdf', TAILLE_MAX_CV + 1))).toBe(
      'Le fichier dépasse 5 Mo',
    );
  });
});
