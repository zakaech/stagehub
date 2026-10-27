package com.stagehub.ai.cv;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PDF fabriqués par PDFBox dans le test : aucun fichier binaire dans le dépôt, chaque cas est lisible ici. */
class ExtractionTexteCvTest {

    private static final String CV = """
            Amine Idrissi - Étudiant ingénieur, filière Systèmes et Réseaux
            Compétences : Docker, Kubernetes, Jenkins, Linux, Ansible
            Expérience : migration de douze applications vers des conteneurs""";

    private final ExtractionTexteCv extraction = new ExtractionTexteCv();

    @Test
    void cvPdf_texteExtraitAvecSesAccents() throws IOException {
        TexteCv texte = extraction.extraire(pdf(CV));

        assertThat(texte.texte()).contains("Étudiant ingénieur", "Docker, Kubernetes, Jenkins", "migration de douze");
        assertThat(texte.pages()).isEqualTo(1);
        assertThat(texte.tronque()).isFalse();
    }

    @Test
    void plusDeDixPages_seulesLesDixPremieresSontLues() throws IOException {
        String[] pages = IntStream.rangeClosed(1, 12)
                .mapToObj(n -> "Page numero " + n + " du CV, avec assez de texte pour etre retenue.")
                .toArray(String[]::new);

        TexteCv texte = extraction.extraire(pdf(pages));

        assertThat(texte.texte()).contains("Page numero 10 ").doesNotContain("Page numero 11 ");
        assertThat(texte.pages()).isEqualTo(12);
        assertThat(texte.tronque()).isTrue();
    }

    @Test
    void texteTropLong_coupeAuDelaDe12000CaracteresSurUneFinDeMot() throws IOException {
        // 6 pages de 45 lignes de 80 caractères : environ 21 000 caractères
        String ligne = "Projet de conteneurisation avec Docker et Kubernetes pour une plateforme web. ";
        String page = IntStream.range(0, 45).mapToObj(i -> ligne).collect(Collectors.joining("\n"));

        TexteCv texte = extraction.extraire(pdf(page, page, page, page, page, page));

        assertThat(texte.texte().length()).isLessThanOrEqualTo(ExtractionTexteCv.CARACTERES_MAX);
        // Coupé sur une fin de mot : le dernier mot est un mot entier de la ligne, pas un morceau
        String dernierMot = texte.texte().substring(texte.texte().lastIndexOf(' ') + 1);
        assertThat(ligne.split(" ")).contains(dernierMot);
        assertThat(texte.tronque()).isTrue();
    }

    @Test
    void pdfSansTexte_cvScanne_illisible() throws IOException {
        assertThatThrownBy(() -> extraction.extraire(pdf("")))
                .isInstanceOf(CvIllisibleException.class)
                .hasMessageContaining("Aucun texte exploitable");
    }

    @Test
    void pasUnPdf_ouPdfAbime_illisible() {
        assertThatThrownBy(() -> extraction.extraire("<html>CV</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CvIllisibleException.class)
                .hasMessage("Le fichier n'est pas un PDF");
        assertThatThrownBy(() -> extraction.extraire("%PDF-1.7\nn'importe quoi".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(CvIllisibleException.class)
                .hasMessage("PDF illisible");
        assertThatThrownBy(() -> extraction.extraire(null))
                .isInstanceOf(CvIllisibleException.class);
    }

    @Test
    void pdfProtegeParMotDePasse_illisible() throws IOException {
        assertThatThrownBy(() -> extraction.extraire(pdfProtege(CV)))
                .isInstanceOf(CvIllisibleException.class)
                .hasMessage("CV protégé par un mot de passe");
    }

    @Test
    void nettoyage_caracteresInvisiblesRetiresEspacesEtLignesVidesRegroupes() {
        String brut = "Kuber​netes et\tDocker   en production\r\n\n\n\n  Projets  \n⁠";

        assertThat(ExtractionTexteCv.nettoyer(brut)).isEqualTo("Kubernetes et Docker en production\n\nProjets");
    }

    /** Un PDF d'une page par texte (police Helvetica, accents compris), lignes séparées par \n. */
    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            ecrire(document, pages);
            return enregistrer(document);
        }
    }

    private static byte[] pdfProtege(String texte) throws IOException {
        try (PDDocument document = new PDDocument()) {
            ecrire(document, texte);
            StandardProtectionPolicy protection = new StandardProtectionPolicy("proprietaire", "utilisateur", new AccessPermission());
            protection.setEncryptionKeyLength(128);
            document.protect(protection);
            return enregistrer(document);
        }
    }

    private static void ecrire(PDDocument document, String... pages) throws IOException {
        PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (String texte : pages) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (texte.isEmpty()) {
                continue;
            }
            try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                flux.beginText();
                flux.setFont(police, 9);
                flux.newLineAtOffset(40, 750);
                for (String ligne : texte.split("\n")) {
                    flux.showText(ligne);
                    flux.newLineAtOffset(0, -15);
                }
                flux.endText();
            }
        }
    }

    private static byte[] enregistrer(PDDocument document) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        document.save(sortie);
        return sortie.toByteArray();
    }
}
