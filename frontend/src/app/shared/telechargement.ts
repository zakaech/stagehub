import { HttpResponse } from '@angular/common/http';

/**
 * Nom proposé par le serveur : filename* (RFC 6266, encodé UTF-8, accents compris) est prioritaire
 * sur filename ; à défaut, le nom par défaut.
 */
export function nomDepuisContentDisposition(entete: string | null, nomParDefaut: string): string {
  if (!entete) {
    return nomParDefaut;
  }
  const encode = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(entete);
  if (encode) {
    try {
      return decodeURIComponent(encode[1].trim());
    } catch {
      // Encodage invalide : on essaie le paramètre filename simple
    }
  }
  const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(entete);
  return simple ? simple[1].trim() : nomParDefaut;
}

/**
 * Déclenche l'enregistrement d'un fichier reçu via HttpClient. Un simple lien <a href> ne conviendrait pas :
 * il n'enverrait pas le jeton JWT. On passe donc par une URL temporaire (blob:) pointant vers les octets reçus.
 */
export function enregistrerFichier(reponse: HttpResponse<Blob>, nomParDefaut: string): void {
  if (!reponse.body) {
    return;
  }
  const url = URL.createObjectURL(reponse.body);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nomDepuisContentDisposition(
    reponse.headers.get('Content-Disposition'),
    nomParDefaut,
  );
  lien.click();
  // Libère la mémoire une fois le téléchargement lancé
  setTimeout(() => URL.revokeObjectURL(url));
}
