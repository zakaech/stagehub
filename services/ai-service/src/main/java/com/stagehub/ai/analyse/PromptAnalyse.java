package com.stagehub.ai.analyse;

import com.stagehub.ai.analyse.OffreClient.OffreAAnalyser;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit les deux messages envoyés au modèle, à partir des fichiers {@code prompts/*.txt}.
 * <ul>
 *   <li><b>Délimitation</b> : le CV est placé entre deux balises qui contiennent une valeur aléatoire, différente à
 *       chaque analyse ; si le CV contient déjà ce texte, il est retiré. Un CV ne peut donc pas « fermer » la balise
 *       pour écrire en dehors ;</li>
 *   <li><b>remplacement en un seul passage</b> : les valeurs (offre, CV) sont insérées dans le gabarit sans être
 *       relues ensuite. Un CV qui contient {@code {{OFFRE}}} ou des accolades reste du texte, jamais un gabarit
 *       (le moteur de gabarits de Spring AI n'est pas utilisé pour cette raison) ;</li>
 *   <li><b>minimisation</b> : adresses email et numéros de téléphone sont masqués avant l'envoi au modèle (inutiles
 *       pour juger l'adéquation à l'offre).</li>
 * </ul>
 */
@Component
public class PromptAnalyse {

    private static final Pattern EMPLACEMENT = Pattern.compile("\\{\\{(OFFRE|BALISE|CV)}}");
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)+");
    /**
     * Numéro qui commence par + (indicatif) ou par 0, suivi d'au moins 8 chiffres séparés ou non par des espaces,
     * points, tirets (06 12 34 56 78, +212 6-12-34-56-78). Une plage d'années (« 2020-2022 ») n'est pas touchée.
     */
    private static final Pattern TELEPHONE = Pattern.compile("(?:\\+\\d{1,3}[ .-]?|0)\\d(?:[ .-]?\\d){7,}");

    private final String systeme;
    private final String gabaritUtilisateur;
    private final SecureRandom aleatoire = new SecureRandom();

    public PromptAnalyse() {
        this.systeme = lire("prompts/analyse-systeme.txt");
        this.gabaritUtilisateur = lire("prompts/analyse-utilisateur.txt");
    }

    /** Les deux messages, et la balise tirée pour cette analyse (pour les tests et le diagnostic). */
    public record Messages(String systeme, String utilisateur, String balise) {
    }

    public Messages construire(OffreAAnalyser offre, String texteCv) {
        byte[] octets = new byte[6];
        aleatoire.nextBytes(octets);
        return construire(offre, texteCv, HexFormat.of().formatHex(octets));
    }

    /** Avec une balise donnée (tests : vérifier qu'un CV qui la contient ne peut pas refermer la délimitation). */
    Messages construire(OffreAAnalyser offre, String texteCv, String balise) {
        String cv = masquerCoordonnees(texteCv).replace(balise, "");
        Map<String, String> valeurs = Map.of("OFFRE", decrire(offre), "BALISE", balise, "CV", cv);
        Matcher emplacement = EMPLACEMENT.matcher(gabaritUtilisateur);
        StringBuilder utilisateur = new StringBuilder();
        while (emplacement.find()) {
            emplacement.appendReplacement(utilisateur, Matcher.quoteReplacement(valeurs.get(emplacement.group(1))));
        }
        emplacement.appendTail(utilisateur);
        return new Messages(systeme, utilisateur.toString(), balise);
    }

    static String masquerCoordonnees(String texte) {
        String sansEmail = EMAIL.matcher(texte).replaceAll("[email masqué]");
        return TELEPHONE.matcher(sansEmail).replaceAll("[téléphone masqué]");
    }

    private static String decrire(OffreAAnalyser offre) {
        return """
                Titre : %s
                Entreprise : %s (%s)
                Domaine : %s
                Description : %s
                Technologies : %s""".formatted(offre.titre(), offre.entrepriseNom(), offre.ville(), offre.domaine(),
                offre.description(), String.join(", ", offre.technologies()));
    }

    private static String lire(String chemin) {
        try {
            return new ClassPathResource(chemin).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt introuvable : " + chemin, e);
        }
    }
}
