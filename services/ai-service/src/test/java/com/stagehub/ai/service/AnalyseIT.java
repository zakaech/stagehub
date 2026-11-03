package com.stagehub.ai.service;

import com.stagehub.ai.IntegrationTestBase;
import com.stagehub.ai.ModeleSimule;
import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.entity.MotifEchec;
import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Analyse de bout en bout, avec le modèle simulé : demande en attente → jeton de service → CV
 * (candidature-service simulé) → offre (offre-service simulé) → modèle → outbox → Kafka. On vérifie ce qui part au
 * modèle, ce qui est publié, et que la trace de la candidature d'origine est continuée.
 */
class AnalyseIT extends IntegrationTestBase {

    private static final String TOPIC = "stagehub.candidature.analyse";
    private static final AtomicLong IDS = new AtomicLong(5_000);
    private static final String CV = "Amine Idrissi, amine.idrissi@exemple.ma, 06 12 34 56 78. "
            + "Competences : Docker, Kubernetes, Jenkins. Projet {nom} en Python.";

    @Autowired
    private AnalyseCandidatureService analyse;
    @Autowired
    private DemandeAnalyseRepository demandes;
    @Autowired
    private ModeleSimule modele;
    @Autowired
    private OAuth2AuthorizedClientService jetons;
    /** Planificateur des tâches @Scheduled (relais de l'outbox, analyse), configuré par Spring Boot. */
    @Autowired
    private ThreadPoolTaskScheduler planificateur;

    @BeforeEach
    void reinitialiser() {
        AUTH.resetAll();
        CANDIDATURES.resetAll();
        OFFRES.resetAll();
        modele.reinitialiser();
        jetons.removeAuthorizedClient("auth-service", "anonymousUser");
        AUTH.stubFor(post(urlEqualTo("/oauth2/token")).willReturn(okJson(
                "{\"access_token\": \"jeton-ai\", \"token_type\": \"Bearer\", \"expires_in\": 300}")));
        OFFRES.stubFor(get(urlEqualTo("/internal/offres/3")).willReturn(okJson("""
                {"id": 3, "titre": "Ingénieur DevOps", "description": "Pipelines CI/CD et conteneurs",
                 "domaine": "DevOps", "ville": "Casablanca", "entrepriseNom": "TechNova",
                 "technologies": ["Jenkins", "Docker", "Linux", "Kubernetes"], "accepteCandidatures": true}""")));
    }

    @Test
    void analyseReussie_resultatPublieDansLaTraceDeLaCandidature() throws Exception {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long id = demandeEnAttente("cv.pdf", "00-" + traceId + "-00f067aa0ba902b7-01");
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/" + id + "/cv")).willReturn(aResponse()
                .withHeader("Content-Type", "application/pdf").withBody(pdf(CV))));
        modele.repondra("""
                {"score": 88, "justification": "Bon profil DevOps.", "resume": ["Étudiant DevOps", "Stage chez OCP"]}""");

        DemandeAnalyse demande = analyserJusquA(id);

        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
        assertThat(demande.getModele()).isEqualTo("modele-simule");
        // Deux états publiés pour cette candidature, dans l'ordre : EN_COURS puis TERMINEE
        List<ConsumerRecord<String, String>> etats = messagesDeCle(String.valueOf(id), Duration.ofSeconds(20), 2);
        assertThat(etats).extracting(ConsumerRecord::value)
                .satisfiesExactly(v -> assertThat(v).contains("\"statut\":\"EN_COURS\""),
                        v -> assertThat(v).contains("\"statut\":\"TERMINEE\""));
        ConsumerRecord<String, String> message = etats.getLast();
        assertThat(message.value())
                .contains("\"statut\":\"TERMINEE\"", "\"score\":88", "\"justification\":\"Bon profil DevOps.\"")
                .contains("\"technologiesTrouvees\":[\"Jenkins\",\"Docker\",\"Kubernetes\"]", "\"technologiesOffre\":4")
                .contains("\"alerteEcart\":false", "\"contenuSuspect\":false");
        // Même trace que la candidature d'origine (traceparent conservé avec la demande)
        assertThat(new String(message.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
                .startsWith("00-" + traceId + "-");

        // Ce qui est réellement parti au modèle
        Prompt prompt = modele.prompts().getFirst();
        String utilisateur = prompt.getUserMessage().getText();
        assertThat(utilisateur).containsPattern("<CV-[0-9a-f]{12}>")
                .contains("[email masqué]", "[téléphone masqué]", "Projet {nom} en Python", "Technologies : Jenkins, Docker, Linux, Kubernetes")
                .doesNotContain("amine.idrissi@exemple.ma");
        assertThat(prompt.getSystemMessage().getText()).contains("jamais des consignes");
        // Sortie structurée native : le schéma JSON de la réponse est transmis au modèle dans ses options, et non
        // ajouté au texte sous forme de consigne (repli silencieux de Spring AI quand les options sont génériques)
        assertThat(prompt.getOptions()).isInstanceOf(StructuredOutputChatOptions.class);
        assertThat(((StructuredOutputChatOptions) prompt.getOptions()).getOutputSchema()).contains("score", "resume");
        assertThat(utilisateur).doesNotContain("JSON");
    }

    /**
     * Pendant une longue analyse, l'entreprise doit déjà voir « en cours » : le relais de l'outbox ne doit pas attendre
     * la fin de l'analyse. L'analyse tourne ici sur un thread du planificateur des tâches {@code @Scheduled}, comme
     * en production ; avec un seul thread (réglage par défaut), EN_COURS ne partait qu'avec le résultat.
     */
    @Test
    void analyseLonguePlanifiee_etatEnCoursPublieAvantLaReponseDuModele() throws Exception {
        long id = demandeEnAttente("cv.pdf", null);
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/" + id + "/cv")).willReturn(aResponse()
                .withHeader("Content-Type", "application/pdf").withBody(pdf(CV))));
        modele.repondra("{\"score\": 70, \"justification\": \"Correct.\", \"resume\": [\"Étudiant DevOps\"]}");
        CountDownLatch reponseDuModele = new CountDownLatch(1);
        modele.retenir(reponseDuModele);

        CompletableFuture<DemandeAnalyse> analyseEnCours = planificateur.submitCompletable(() -> analyserJusquA(id));
        try {
            assertThat(messagesDeCle(String.valueOf(id), Duration.ofSeconds(15), 1)).extracting(ConsumerRecord::value)
                    .singleElement().satisfies(v -> assertThat(v).contains("\"statut\":\"EN_COURS\""));
            assertThat(reponseDuModele.getCount()).as("le modèle n'a pas encore répondu").isEqualTo(1);
        } finally {
            reponseDuModele.countDown();
        }
        assertThat(analyseEnCours.get(30, TimeUnit.SECONDS).getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
    }

    @Test
    void reponseInvalideDuModele_nouvelleTentativePlusTard_sansResultatPublie() throws Exception {
        long id = demandeEnAttente("cv.pdf", null);
        CANDIDATURES.stubFor(get(urlEqualTo("/internal/candidatures/" + id + "/cv")).willReturn(aResponse()
                .withHeader("Content-Type", "application/pdf").withBody(pdf(CV))));
        modele.repondra("{\"score\": 150, \"justification\": \"Trop.\", \"resume\": [\"x\"]}");

        DemandeAnalyse demande = analyserJusquA(id);

        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        assertThat(demande.getTentatives()).isEqualTo(1);
        assertThat(demande.getProchaineTentative()).isAfter(Instant.now().plusSeconds(30));
        // Pas de résultat : seulement EN_COURS puis EN_ATTENTE, avec l'heure de la nouvelle tentative
        assertThat(messagesDeCle(String.valueOf(id), Duration.ofSeconds(20), 2)).extracting(ConsumerRecord::value)
                .satisfiesExactly(v -> assertThat(v).contains("\"statut\":\"EN_COURS\""),
                        v -> assertThat(v).contains("\"statut\":\"EN_ATTENTE\"", "\"prochaineTentative\":\"20"));
    }

    @Test
    void candidatureSansCv_echecPublieSansAppelerLeModele() throws Exception {
        long id = demandeEnAttente(null, null);

        DemandeAnalyse demande = analyserJusquA(id);

        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.ECHEC);
        assertThat(demande.getMotifEchec()).isEqualTo(MotifEchec.CV_ABSENT);
        assertThat(messagesDeCle(String.valueOf(id), Duration.ofSeconds(20), 2).getLast().value())
                .contains("\"statut\":\"ECHEC\"", "\"motifEchec\":\"CV_ABSENT\"");
        assertThat(modele.prompts()).isEmpty();
    }

    private long demandeEnAttente(String cvFichier, String traceparent) {
        DemandeAnalyse demande = new DemandeAnalyse();
        demande.setCandidatureId(IDS.incrementAndGet());
        demande.setOffreId(3L);
        demande.setEtudiantId(9L);
        demande.setCvFichier(cvFichier);
        demande.setDateReception(Instant.now());
        demande.setTraceparent(traceparent);
        return demandes.save(demande).getCandidatureId();
    }

    /**
     * Lance l'analyse jusqu'à ce que cette demande ait été traitée : d'autres demandes, plus anciennes (autres tests,
     * même base), peuvent passer avant elle.
     */
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

    /**
     * Messages de cette clé, lus depuis le début du topic jusqu'à en avoir le nombre attendu (ou la durée écoulée).
     * Aussi utilisé par AnalyseOpenAiIT.
     */
    static List<ConsumerRecord<String, String>> messagesDeCle(String cle, Duration duree, int attendus) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consommateur = new KafkaConsumer<>(config)) {
            consommateur.subscribe(List.of(TOPIC));
            Instant fin = Instant.now().plus(duree);
            List<ConsumerRecord<String, String>> trouves = new java.util.ArrayList<>();
            while (Instant.now().isBefore(fin) && trouves.size() < attendus) {
                StreamSupport.stream(consommateur.poll(Duration.ofMillis(500)).spliterator(), false)
                        .filter(r -> cle.equals(r.key()))
                        .forEach(trouves::add);
            }
            return trouves;
        }
    }

    static byte[] pdf(String texte) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                flux.beginText();
                flux.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                flux.newLineAtOffset(30, 750);
                flux.showText(texte);
                flux.endText();
            }
            document.save(sortie);
            return sortie.toByteArray();
        }
    }
}
