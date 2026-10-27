package com.stagehub.ai.analyse;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que le modèle doit renvoyer (phase 8 d). Spring AI en tire un schéma JSON, imposé au modèle (Ollama : paramètre
 * {@code format}) : la réponse a forcément cette forme. Son contenu, lui, est revérifié par {@link #erreurs()} : un
 * schéma ne garantit ni les bornes du score ni la longueur des textes.
 */
public record ReponseModele(
        @JsonPropertyDescription("Adéquation du CV à l'offre, entier de 0 à 100")
        Integer score,
        @JsonPropertyDescription("Deux phrases au plus, en français, factuelles")
        String justification,
        @JsonPropertyDescription("Cinq lignes au plus qui résument le CV, en français")
        List<String> resume
) {

    static final int JUSTIFICATION_MAX = 600;
    static final int LIGNES_MAX = 5;
    static final int LIGNE_MAX = 300;

    /** Ce qui ne va pas dans la réponse (vide si elle est utilisable). */
    public List<String> erreurs() {
        List<String> erreurs = new ArrayList<>();
        if (score == null || score < 0 || score > 100) {
            erreurs.add("score absent ou hors de 0..100 : " + score);
        }
        if (justification == null || justification.isBlank() || justification.length() > JUSTIFICATION_MAX) {
            erreurs.add("justification vide ou trop longue");
        }
        if (resume == null || resume.isEmpty() || resume.size() > LIGNES_MAX
                || resume.stream().anyMatch(ligne -> ligne == null || ligne.isBlank() || ligne.length() > LIGNE_MAX)) {
            erreurs.add("résumé vide, de plus de " + LIGNES_MAX + " lignes, ou avec une ligne vide ou trop longue");
        }
        return erreurs;
    }
}
