package com.stagehub.analyse.dto;

import java.util.Locale;

/**
 * Fournisseur du modèle qui analyse les CV, même valeur qu'ai-service ({@code IA_FOURNISSEUR}). Il décide du
 * texte d'information montré à l'étudiant : avec OpenAI, le CV quitte nos serveurs.
 */
public enum FournisseurIa {

    /** Modèle local (Ollama) : le CV reste sur le serveur de StageHub. */
    OLLAMA,
    /** OpenAI : le CV, email et téléphone masqués, est envoyé à OpenAI. */
    OPENAI;

    /** Valeur de {@code IA_FOURNISSEUR} (« ollama », « openai », casse indifférente) ; toute autre valeur est refusée. */
    public static FournisseurIa depuis(String valeur) {
        String nom = valeur == null ? "" : valeur.strip().toUpperCase(Locale.ROOT);
        for (FournisseurIa fournisseur : values()) {
            if (fournisseur.name().equals(nom)) {
                return fournisseur;
            }
        }
        throw new IllegalArgumentException("IA_FOURNISSEUR inconnu : « " + valeur + " » (attendu : ollama ou openai)");
    }
}
