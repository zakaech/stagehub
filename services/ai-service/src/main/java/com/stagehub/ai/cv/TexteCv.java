package com.stagehub.ai.cv;

/**
 * Texte extrait d'un CV, prêt à être donné au modèle.
 *
 * @param texte  texte nettoyé (caractères invisibles retirés, espaces regroupés)
 * @param pages  nombre de pages du PDF
 * @param tronque vrai si le texte a été coupé (pages ou caractères au-delà des limites)
 */
public record TexteCv(String texte, int pages, boolean tronque) {
}
