package com.stagehub.ai.cv;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Texte d'un CV PDF (Apache PDFBox), borné et nettoyé avant d'être donné au modèle.
 * <ul>
 *   <li>10 pages et 12 000 caractères au plus : un CV n'en a pas besoin, et la durée de l'analyse (processeur seul)
 *       croît avec la longueur du texte ;</li>
 *   <li>caractères invisibles retirés (espaces de largeur nulle, caractères de contrôle) : ils ne servent à rien au
 *       modèle et peuvent cacher du texte ou découper des mots ;</li>
 *   <li>ce qui est écrit en blanc ou en tout petit reste extrait : PDFBox lit le texte, pas son apparence. Une
 *       consigne cachée de cette façon arrive donc jusqu'au modèle (voir les protections de l'analyse, phase 8 d).</li>
 * </ul>
 * Ce service est le seul à ouvrir les PDF des étudiants pour l'analyse : un PDF piégé n'atteint que lui, qui ne
 * détient aucune donnée des autres services.
 */
@Component
public class ExtractionTexteCv {

    static final int PAGES_MAX = 10;
    static final int CARACTERES_MAX = 12_000;
    static final int CARACTERES_MIN = 50;

    private static final byte[] SIGNATURE_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** Caractères de mise en forme invisibles (espace de largeur nulle, marques de direction...) : supprimés. */
    private static final Pattern INVISIBLES = Pattern.compile("\\p{Cf}");
    /** Caractères de contrôle (tabulation, retour chariot...) sauf le saut de ligne, et espaces insécables : espace. */
    private static final Pattern CONTROLES = Pattern.compile("[\\p{Cc}\\u00A0&&[^\\n]]");

    public TexteCv extraire(byte[] pdf) {
        if (!estUnPdf(pdf)) {
            throw new CvIllisibleException("Le fichier n'est pas un PDF", null);
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(pages, PAGES_MAX));
            String texte = nettoyer(stripper.getText(document));
            if (texte.replaceAll("\\s", "").length() < CARACTERES_MIN) {
                throw new CvIllisibleException("Aucun texte exploitable dans le CV (document scanné ?)", null);
            }
            boolean tronque = pages > PAGES_MAX;
            if (texte.length() > CARACTERES_MAX) {
                texte = couper(texte);
                tronque = true;
            }
            return new TexteCv(texte, pages, tronque);
        } catch (InvalidPasswordException e) {
            throw new CvIllisibleException("CV protégé par un mot de passe", e);
        } catch (IOException e) {
            throw new CvIllisibleException("PDF illisible", e);
        }
    }

    static String nettoyer(String brut) {
        String texte = INVISIBLES.matcher(brut).replaceAll("");
        texte = CONTROLES.matcher(texte).replaceAll(" ");
        StringBuilder resultat = new StringBuilder();
        int lignesVides = 0;
        for (String ligne : texte.split("\n")) {
            String propre = ligne.strip().replaceAll(" {2,}", " ");
            if (propre.isEmpty()) {
                // Une seule ligne vide entre deux paragraphes
                if (++lignesVides > 1 || resultat.isEmpty()) {
                    continue;
                }
            } else {
                lignesVides = 0;
            }
            resultat.append(propre).append('\n');
        }
        return resultat.toString().strip();
    }

    /** Coupe à la limite, sur une fin de mot. */
    private static String couper(String texte) {
        int fin = texte.lastIndexOf(' ', CARACTERES_MAX);
        return texte.substring(0, fin > CARACTERES_MAX / 2 ? fin : CARACTERES_MAX).strip();
    }

    private static boolean estUnPdf(byte[] contenu) {
        if (contenu == null || contenu.length < SIGNATURE_PDF.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE_PDF.length; i++) {
            if (contenu[i] != SIGNATURE_PDF[i]) {
                return false;
            }
        }
        return true;
    }
}
