package com.stagehub.ai.analyse;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comptage déterministe : quelles technologies de l'offre apparaissent dans le texte
 * du CV ? Sans le modèle, donc sans hasard ni influence d'une consigne cachée : un repère vérifiable, affiché à côté
 * du score (« 3 sur 4 »).
 * <ul>
 *   <li>insensible à la casse et aux accents ; mot entier : « Java » ne compte pas dans « JavaScript » ;</li>
 *   <li><b>limites</b> : les variantes ne sont pas reconnues (« K8s » pour Kubernetes, « Postgres » pour PostgreSQL),
 *       et une technologie citée sans être maîtrisée (« notions de Kubernetes ») compte quand même ; un CV qui
 *       empile les mots-clés gonfle ce comptage. C'est un repère, pas une note.</li>
 * </ul>
 */
@Component
public class ComptageTechnologies {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    /**
     * @param trouvees technologies de l'offre retrouvées dans le CV, dans l'ordre de l'offre
     * @param total    nombre de technologies distinctes de l'offre
     */
    public record Correspondance(List<String> trouvees, int total) {

        /** Part des technologies retrouvées, de 0 à 100 ; null si l'offre n'en cite aucune. */
        public Integer pourcentage() {
            return total == 0 ? null : Math.round(100f * trouvees.size() / total);
        }
    }

    public Correspondance compter(List<String> technologies, String texteCv) {
        String texte = normaliser(texteCv);
        Set<String> distinctes = new LinkedHashSet<>();
        Set<String> trouvees = new LinkedHashSet<>();
        for (String technologie : technologies) {
            String cle = normaliser(technologie).strip();
            if (cle.isEmpty() || !distinctes.add(cle)) {
                continue;
            }
            // Mot entier : ni lettre ni chiffre juste avant ou juste après (« C++ », « Node.js » restent possibles)
            Pattern motEntier = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(cle) + "(?![\\p{L}\\p{N}])");
            if (motEntier.matcher(texte).find()) {
                trouvees.add(technologie.strip());
            }
        }
        return new Correspondance(List.copyOf(trouvees), distinctes.size());
    }

    private static String normaliser(String texte) {
        String sansAccents = ACCENTS.matcher(Normalizer.normalize(texte, Normalizer.Form.NFD)).replaceAll("");
        return sansAccents.toLowerCase(Locale.ROOT);
    }
}
