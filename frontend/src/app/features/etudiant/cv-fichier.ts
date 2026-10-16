/** Même limite que le backend (stagehub.cv.taille-max) : 5 Mo = 5 × 1024 × 1024 octets. */
export const TAILLE_MAX_CV = 5 * 1024 * 1024;

/**
 * Pré-vérification côté navigateur, pour un retour immédiat sans envoyer le fichier.
 * Ce n'est qu'un confort : le serveur revérifie tout et lit le contenu réel (%PDF-), que l'on ne peut pas falsifier.
 * Renvoie un message d'erreur, ou null si le fichier peut être envoyé.
 */
export function verifierFichierCv(fichier: File): string | null {
  if (fichier.size === 0) {
    return 'Le fichier est vide';
  }
  const estPdf = fichier.type === 'application/pdf' || fichier.name.toLowerCase().endsWith('.pdf');
  if (!estPdf) {
    return 'Seuls les fichiers PDF sont acceptés';
  }
  if (fichier.size > TAILLE_MAX_CV) {
    return 'Le fichier dépasse 5 Mo';
  }
  return null;
}
