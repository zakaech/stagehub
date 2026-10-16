package com.stagehub.cv.service;

import com.stagehub.common.exception.FichierNonPdfException;
import com.stagehub.common.exception.FichierTropVolumineuxException;
import com.stagehub.common.exception.FichierVideException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Vérifie qu'un fichier envoyé est un PDF acceptable. Le nom (".pdf") et le Content-Type ("application/pdf")
 * sont fournis par le client et donc falsifiables : seule compte la signature du contenu, "%PDF-",
 * présente au début de tout fichier PDF (les "magic bytes").
 */
public final class PdfValidator {

    private static final byte[] SIGNATURE_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private PdfValidator() {
    }

    public static void valider(MultipartFile fichier, DataSize tailleMax) {
        if (fichier == null || fichier.isEmpty()) {
            throw new FichierVideException();
        }
        if (fichier.getSize() > tailleMax.toBytes()) {
            throw new FichierTropVolumineuxException(tailleMax.toMegabytes());
        }
        if (!commenceParSignaturePdf(fichier)) {
            throw new FichierNonPdfException();
        }
    }

    private static boolean commenceParSignaturePdf(MultipartFile fichier) {
        try (InputStream contenu = fichier.getInputStream()) {
            return Arrays.equals(contenu.readNBytes(SIGNATURE_PDF.length), SIGNATURE_PDF);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible", e);
        }
    }
}
