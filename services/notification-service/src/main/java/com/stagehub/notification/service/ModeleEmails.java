package com.stagehub.notification.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Charge les modèles d'emails depuis {@code classpath:emails/<nom>.txt} et remplace les variables ${...}.
 *
 * <p>Format d'un modèle : première ligne "Objet : ...", une ligne vide, puis le corps du message.
 * Une variable absente fait échouer le rendu (pas d'email envoyé avec "${prenom}" en clair).
 */
@Component
public class ModeleEmails {

    private static final String PREFIXE_OBJET = "Objet :";

    // Dernier argument false : une variable inconnue lève une exception au lieu d'être laissée telle quelle
    private final PropertyPlaceholderHelper remplacement = new PropertyPlaceholderHelper("${", "}", null, null, false);
    private final ResourceLoader resourceLoader;

    public ModeleEmails(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public record EmailRendu(String objet, String corps) {
    }

    public EmailRendu rendre(String nomModele, Map<String, String> variables) {
        String texte = remplacement.replacePlaceholders(lire(nomModele), variables::get);
        int finPremiereLigne = texte.indexOf('\n');
        String premiereLigne = finPremiereLigne < 0 ? texte : texte.substring(0, finPremiereLigne);
        if (!premiereLigne.startsWith(PREFIXE_OBJET)) {
            throw new IllegalStateException("Le modèle " + nomModele + " doit commencer par « " + PREFIXE_OBJET + " »");
        }
        String corps = finPremiereLigne < 0 ? "" : texte.substring(finPremiereLigne + 1).strip();
        return new EmailRendu(premiereLigne.substring(PREFIXE_OBJET.length()).strip(), corps);
    }

    private String lire(String nomModele) {
        Resource modele = resourceLoader.getResource("classpath:emails/" + nomModele + ".txt");
        try {
            return modele.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Modèle d'email introuvable : " + nomModele, e);
        }
    }
}
