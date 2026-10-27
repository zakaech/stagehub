package com.stagehub.ai.cv;

import com.stagehub.ai.IntegrationTestBase;
import feign.FeignException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lecture du CV de bout en bout (phase 8 c) : jeton de service demandé à auth-service (flux client credentials),
 * puis CV lu dans candidature-service avec ce jeton, puis texte extrait. Les deux services sont simulés (WireMock) :
 * on vérifie exactement ce qu'ai-service leur envoie.
 */
class LectureCvIT extends IntegrationTestBase {

    private static final String JETON = "jeton-de-service-ai-1";

    @Autowired
    private LectureCv lectureCv;
    @Autowired
    private OAuth2AuthorizedClientService jetonsObtenus;

    @BeforeEach
    void reinitialiser() {
        AUTH.resetAll();
        CANDIDATURES.resetAll();
        // Jeton gardé en mémoire d'un test à l'autre (même contexte) : chaque test repart sans jeton
        jetonsObtenus.removeAuthorizedClient("auth-service", "anonymousUser");
        AUTH.stubFor(post(urlEqualTo("/oauth2/token")).willReturn(okJson("""
                {"access_token": "%s", "token_type": "Bearer", "expires_in": 300}""".formatted(JETON))));
    }

    @Test
    void cvLuAvecUnJetonDeService_demandeUneSeuleFoisPuisReutilise() throws IOException {
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/41/cv")).willReturn(aResponse()
                .withHeader("Content-Type", "application/pdf").withBody(pdf("Compétences : Docker, Kubernetes, Jenkins, Linux, Ansible, pipelines CI/CD et supervision"))));

        TexteCv texte = lectureCv.texteDuCv(41L);
        lectureCv.texteDuCv(41L);

        assertThat(texte.texte()).contains("Compétences : Docker, Kubernetes, Jenkins, Linux");
        // Un seul jeton demandé, avec l'identifiant et le secret d'ai-service (Basic), pour deux lectures
        String identifiants = Base64.getEncoder().encodeToString(
                ("ai-service:" + SECRET_AI_SERVICE).getBytes(StandardCharsets.UTF_8));
        AUTH.verify(1, postRequestedFor(urlEqualTo("/oauth2/token"))
                .withHeader("Authorization", equalTo("Basic " + identifiants))
                .withRequestBody(containing("grant_type=client_credentials")));
        CANDIDATURES.verify(2, getRequestedFor(urlEqualTo("/internal/candidatures/41/cv"))
                .withHeader("Authorization", equalTo("Bearer " + JETON)));
    }

    @Test
    void candidatureSansCv_404_cvAbsent() {
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/42/cv")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> lectureCv.texteDuCv(42L)).isInstanceOf(CvAbsentException.class);
    }

    @Test
    void candidatureServiceEnPanne_erreurPropagee_pourUneNouvelleTentativePlusTard() {
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/43/cv")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> lectureCv.texteDuCv(43L)).isInstanceOf(FeignException.ServiceUnavailable.class);
    }

    @Test
    void jetonRefuseParAuthService_aucunAppelACandidatureService() {
        AUTH.stubFor(post(urlEqualTo("/oauth2/token")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json").withBody("{\"error\": \"invalid_client\"}")));

        assertThatThrownBy(() -> lectureCv.texteDuCv(44L)).hasMessageContaining("invalid_client");
        CANDIDATURES.verify(0, anyRequestedFor(anyUrl()));
    }

    private static byte[] pdf(String texte) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                flux.beginText();
                flux.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                flux.newLineAtOffset(50, 750);
                flux.showText(texte);
                flux.endText();
            }
            document.save(sortie);
            return sortie.toByteArray();
        }
    }
}
