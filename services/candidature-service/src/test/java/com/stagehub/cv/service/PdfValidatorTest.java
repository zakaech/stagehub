package com.stagehub.cv.service;

import com.stagehub.common.exception.FichierNonPdfException;
import com.stagehub.common.exception.FichierTropVolumineuxException;
import com.stagehub.common.exception.FichierVideException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfValidatorTest {

    private static final DataSize CINQ_MO = DataSize.ofMegabytes(5);
    static final byte[] PDF_MINIMAL = "%PDF-1.7\n%âãÏÓ\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".getBytes();
    private static final byte[] DEBUT_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @Test
    void vraiPdf_estAccepte() {
        assertThatCode(() -> PdfValidator.valider(fichier("cv.pdf", "application/pdf", PDF_MINIMAL), CINQ_MO))
                .doesNotThrowAnyException();
    }

    @Test
    void contenuNonPdf_estRefuseMalgreExtensionEtContentType() {
        // Un PNG renommé cv.pdf et annoncé application/pdf : seuls les premiers octets font foi
        assertThatThrownBy(() -> PdfValidator.valider(fichier("cv.pdf", "application/pdf", DEBUT_PNG), CINQ_MO))
                .isInstanceOf(FichierNonPdfException.class);
    }

    @Test
    void pdfReelSousUnAutreNom_estAccepte() {
        // Inversement, le nom et le type annoncés n'entrent pas en compte dans la décision
        assertThatCode(() -> PdfValidator.valider(fichier("scan.bin", "application/octet-stream", PDF_MINIMAL), CINQ_MO))
                .doesNotThrowAnyException();
    }

    @Test
    void fichierPlusCourtQueLaSignature_estRefuse() {
        assertThatThrownBy(() -> PdfValidator.valider(fichier("cv.pdf", "application/pdf", "%PD".getBytes()), CINQ_MO))
                .isInstanceOf(FichierNonPdfException.class);
    }

    @Test
    void fichierVide_estRefuse() {
        assertThatThrownBy(() -> PdfValidator.valider(fichier("cv.pdf", "application/pdf", new byte[0]), CINQ_MO))
                .isInstanceOf(FichierVideException.class);
    }

    @Test
    void fichierTropVolumineux_estRefuse() {
        byte[] sixMo = new byte[6 * 1024 * 1024];
        System.arraycopy(PDF_MINIMAL, 0, sixMo, 0, PDF_MINIMAL.length);

        assertThatThrownBy(() -> PdfValidator.valider(fichier("cv.pdf", "application/pdf", sixMo), CINQ_MO))
                .isInstanceOf(FichierTropVolumineuxException.class)
                .hasMessageContaining("5 Mo");
    }

    @Test
    void aucunFichier_estRefuse() {
        assertThatThrownBy(() -> PdfValidator.valider(null, CINQ_MO)).isInstanceOf(FichierVideException.class);
    }

    @Test
    void lectureImpossible_erreurExplicite() throws Exception {
        MultipartFile fichier = mock(MultipartFile.class);
        when(fichier.getSize()).thenReturn(10L);
        when(fichier.getInputStream()).thenThrow(new IOException("flux fermé"));

        assertThatThrownBy(() -> PdfValidator.valider(fichier, CINQ_MO)).isInstanceOf(UncheckedIOException.class);
    }

    private static MockMultipartFile fichier(String nom, String type, byte[] contenu) {
        return new MockMultipartFile("fichier", nom, type, contenu);
    }
}
