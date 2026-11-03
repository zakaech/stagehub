package com.stagehub.ai.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.stagehub.ai.IntegrationTestBase;
import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Passage à OpenAI par la configuration, sans clé ni appel réel : le vrai client OpenAI de Spring AI
 * ({@code IA_FOURNISSEUR=openai}) parle à un faux serveur OpenAI (WireMock). On vérifie ce qui part chez OpenAI (clé,
 * modèle, schéma JSON imposé, échanges non conservés, coordonnées masquées) et le traitement de la réponse.
 * Contexte Spring à part (fournisseur différent) ; le modèle simulé des autres tests n'y est pas créé.
 */
class AnalyseOpenAiIT extends IntegrationTestBase {

    private static final String CLE = "sk-test-cle-openai";
    private static final AtomicLong IDS = new AtomicLong(7_000);
    private static final String CV = "Amine Idrissi, amine.idrissi@exemple.ma, 06 12 34 56 78. "
            + "Competences : Docker, Kubernetes, Jenkins. Stage DevOps chez OCP.";
    /** Faux serveur OpenAI : POST /v1/chat/completions. */
    private static final WireMockServer OPENAI = new WireMockServer(options().dynamicPort());

    static {
        OPENAI.start();
    }

    @DynamicPropertySource
    static void openAi(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + OPENAI.port() + "/v1");
        registry.add("spring.ai.openai.api-key", () -> CLE);
    }

    @AfterAll
    static void arreter() {
        OPENAI.stop();
    }

    @Autowired
    private AnalyseCandidatureService analyse;
    @Autowired
    private DemandeAnalyseRepository demandes;
    @Autowired
    private ChatModel modele;
    @Autowired
    private OAuth2AuthorizedClientService jetons;

    @BeforeEach
    void reinitialiser() {
        AUTH.resetAll();
        CANDIDATURES.resetAll();
        OFFRES.resetAll();
        OPENAI.resetAll();
        jetons.removeAuthorizedClient("auth-service", "anonymousUser");
        AUTH.stubFor(post(urlEqualTo("/oauth2/token")).willReturn(okJson(
                "{\"access_token\": \"jeton-ai\", \"token_type\": \"Bearer\", \"expires_in\": 300}")));
        OFFRES.stubFor(get(urlEqualTo("/internal/offres/3")).willReturn(okJson("""
                {"id": 3, "titre": "Ingénieur DevOps", "description": "Pipelines CI/CD et conteneurs",
                 "domaine": "DevOps", "ville": "Casablanca", "entrepriseNom": "TechNova",
                 "technologies": ["Jenkins", "Docker", "Linux", "Kubernetes"], "accepteCandidatures": true}""")));
    }

    @Test
    void fournisseurOpenAi_clientOpenAiDeSpringAi() {
        assertThat(modele).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void analyseParOpenAi_requeteConformeEtResultatPublie() throws Exception {
        long id = demandeAvecCv();
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(okJson(completion("""
                {"score": 82, "justification": "Profil DevOps solide.", "resume": ["Étudiant DevOps", "Stage chez OCP"]}"""))));

        DemandeAnalyse demande = analyserJusquA(id);

        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
        assertThat(demande.getModele()).isEqualTo("gpt-5-mini-2025-08-07");
        List<ConsumerRecord<String, String>> etats = AnalyseIT.messagesDeCle(String.valueOf(id), Duration.ofSeconds(20), 2);
        assertThat(etats.getLast().value()).contains("\"statut\":\"TERMINEE\"", "\"score\":82",
                "\"technologiesTrouvees\":[\"Jenkins\",\"Docker\",\"Kubernetes\"]");

        // Ce qui est réellement parti chez OpenAI
        List<LoggedRequest> requetes = OPENAI.findAll(postRequestedFor(urlPathEqualTo("/v1/chat/completions")));
        assertThat(requetes).hasSize(1);
        LoggedRequest requete = requetes.getFirst();
        assertThat(requete.getHeader("Authorization")).isEqualTo("Bearer " + CLE);
        String corps = requete.getBodyAsString();
        assertThat(corps)
                .contains("\"model\":\"gpt-5-mini\"")
                // Sortie structurée native : le schéma de ReponseModele part dans response_format, pas dans le texte
                .contains("\"response_format\"", "\"json_schema\"", "\"score\"", "\"resume\"")
                .contains("\"store\":false")
                // Les modèles GPT-5 refusent une température autre que celle par défaut
                .doesNotContain("\"temperature\"")
                .containsPattern("<CV-[0-9a-f]{12}>")
                .contains("[email masqué]", "[téléphone masqué]")
                .doesNotContain("amine.idrissi@exemple.ma");
    }

    @Test
    void cleRefuseeParOpenAi_nouvelleTentativePlusTardSansRejeuImmediat() throws Exception {
        long id = demandeAvecCv();
        OPENAI.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": {\"message\": \"Incorrect API key provided\", \"type\": \"invalid_request_error\"}}")));

        DemandeAnalyse demande = analyserJusquA(id);

        // Panne côté fournisseur : la demande attend une nouvelle tentative (pas d'échec définitif tout de suite)
        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        assertThat(demande.getProchaineTentative()).isAfter(Instant.now().plusSeconds(30));
        // Une seule requête : les nouvelles tentatives du SDK sont coupées (spring.ai.openai.chat.max-retries: 0)
        assertThat(OPENAI.findAll(postRequestedFor(urlPathEqualTo("/v1/chat/completions")))).hasSize(1);
    }

    private long demandeAvecCv() throws Exception {
        DemandeAnalyse demande = new DemandeAnalyse();
        demande.setCandidatureId(IDS.incrementAndGet());
        demande.setOffreId(3L);
        demande.setEtudiantId(9L);
        demande.setCvFichier("cv.pdf");
        demande.setDateReception(Instant.now());
        long id = demandes.save(demande).getCandidatureId();
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/" + id + "/cv")).willReturn(aResponse()
                .withHeader("Content-Type", "application/pdf").withBody(AnalyseIT.pdf(CV))));
        return id;
    }

    /** Lance l'analyse jusqu'à ce que cette demande ait été traitée (d'autres demandes peuvent passer avant). */
    private DemandeAnalyse analyserJusquA(long id) {
        for (int i = 0; i < 50; i++) {
            Optional<DemandeAnalyse> demande = demandes.findById(id);
            if (demande.isPresent() && demande.get().getTentatives() > 0 && demande.get().getStatut() != StatutAnalyse.EN_COURS) {
                return demande.get();
            }
            if (!analyse.traiterLaProchaine()) {
                break;
            }
        }
        return demandes.findById(id).orElseThrow();
    }

    /** Réponse de l'API Chat Completions d'OpenAI dont le message contient ce JSON. */
    private static String completion(String contenu) {
        String echappe = contenu.strip().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        return """
                {"id": "chatcmpl-test", "object": "chat.completion", "created": 1793260000,
                 "model": "gpt-5-mini-2025-08-07",
                 "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
                              "message": {"role": "assistant", "content": "%s", "refusal": null}}],
                 "usage": {"prompt_tokens": 900, "completion_tokens": 120, "total_tokens": 1020}}""".formatted(echappe);
    }
}
