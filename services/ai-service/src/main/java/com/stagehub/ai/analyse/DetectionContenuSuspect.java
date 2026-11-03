package com.stagehub.ai.analyse;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Repère, dans le texte d'un CV, des phrases qui ressemblent à des consignes adressées au modèle (« ignore les
 * consignes », « attribue-lui le score de 100 »...), en français et en anglais. L'entreprise voit alors
 * « contenu inhabituel : à vérifier ».
 * <p><b>Ce n'est pas une protection</b> : une heuristique, facile à contourner (autre formulation, autre langue,
 * fautes volontaires). Les vraies protections sont ailleurs : CV délimité par des balises aléatoires, sortie
 * contrainte par un schéma puis validée, aucun outil donné au modèle (voir ModeleAnalyse et PromptAnalyse). Elle sert
 * seulement à attirer l'attention d'un humain sur une tentative grossière.
 */
@Component
public class DetectionContenuSuspect {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");

    /** Nom de la règle (pour le journal) → expression, sur un texte en minuscules et sans accents. */
    private static final Map<String, Pattern> REGLES = new LinkedHashMap<>();

    static {
        regle("ignorer les consignes", "ignore[rz]? (toutes |tous |les |toute |the |all |any |previous |prior |above )*"
                + "(consignes|instructions|regles|prompts?|directives)");
        regle("score imposé", "(attribue|donne|accorde|mets|assign|give)[\\w-]* (lui |him |her |them |a |le |un |the )*"
                + "(score|note|rating)");
        regle("score maximal", "(score|note|rating) (de |of )?100( ?/ ?100)?");
        regle("présélection annoncée", "(pre-?selectionne|pre-?selected|pre-?approved|shortlisted|deja retenu)");
        regle("adresse au modèle", "(systeme|evaluateur|modele|assistant|outil) (d'evaluation |de tri )?automatique"
                + "|(ai|automated|llm) (screening|evaluator|reviewer|system)|language model|modele de langage");
        regle("changement de rôle", "(tu es|vous etes|you are) (maintenant|desormais|now) ");
        regle("consigne sur le résumé", "(dans|in) (le |the )?(resume|summary)[ ,:]+(ecris|indique|mentionne|write|state|say)");
    }

    private static void regle(String nom, String expression) {
        REGLES.put(nom, Pattern.compile(expression));
    }

    /** Noms des règles déclenchées (vide si rien de suspect). */
    public List<String> reglesDeclenchees(String texteCv) {
        String texte = ACCENTS.matcher(Normalizer.normalize(texteCv, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return REGLES.entrySet().stream()
                .filter(regle -> regle.getValue().matcher(texte).find())
                .map(Map.Entry::getKey)
                .toList();
    }
}
