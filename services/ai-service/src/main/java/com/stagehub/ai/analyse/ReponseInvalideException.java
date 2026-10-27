package com.stagehub.ai.analyse;

/** Réponse du modèle inutilisable (score hors bornes, texte vide ou trop long...) : la demande sera retentée. */
public class ReponseInvalideException extends RuntimeException {

    public ReponseInvalideException(String message) {
        super("Réponse du modèle invalide : " + message);
    }
}
