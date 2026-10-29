package com.stagehub.analyse;

import com.stagehub.ApiDeTest;
import com.stagehub.IntegrationTestBase;
import com.stagehub.analyse.repository.EvenementTraiteRepository;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutAnalyse;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * États de l'analyse reçus d'ai-service par Kafka (phase 8 e), avec un vrai Kafka (Testcontainers) : enregistrés dans
 * la candidature, montrés à l'entreprise (jamais à l'étudiant), tri par score au choix, résultat orphelin ignoré,
 * message illisible envoyé dans le topic d'erreurs.
 */
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "stagehub.erreurs.tentatives=2",
        "stagehub.erreurs.premier-delai=200ms"
})
@ExtendWith(OutputCaptureExtension.class)
class AnalyseResultatIT extends IntegrationTestBase {

    private static final String TOPIC = "stagehub.candidature.analyse";

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    static {
        KAFKA.start();
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private CandidatureRepository candidatureRepository;
    @Autowired
    private EvenementTraiteRepository evenementsTraites;

    @Test
    void etatsDeLAnalyse_montresALEntreprise_jamaisALEtudiant() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();
        // À la candidature : envoyée à l'analyse ; IA arrêtée dans ce contexte de test → « désactivée » à l'affichage
        assertThat(analyse(s.candidatureId()).getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        api.lire("/api/v1/entreprise/offres/{id}/candidatures", s.entreprise().token(), s.offreId())
                .andExpect(jsonPath("$.content[0].analyse.statut").value("DESACTIVEE"));

        // État intermédiaire, sans les champs du résultat : accepté (lecteur tolérant)
        publier(s.candidatureId(), """
                {"candidatureId": %d, "statut": "EN_COURS", "date": "2026-10-28T10:00:00Z"}""".formatted(s.candidatureId()));
        attendre(() -> analyse(s.candidatureId()).getStatut() == StatutAnalyse.EN_COURS);
        api.lire("/api/v1/entreprise/offres/{id}/candidatures", s.entreprise().token(), s.offreId())
                .andExpect(jsonPath("$.content[0].analyse.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.content[0].analyse.score").doesNotExist());
        publier(s.candidatureId(), """
                {"candidatureId": %d, "statut": "TERMINEE", "score": 85, "justification": "Bon profil DevOps.",
                 "resume": ["Étudiant ingénieur", "Stage chez OCP"], "technologiesTrouvees": ["Docker", "Jenkins"],
                 "technologiesOffre": 4, "alerteEcart": false, "contenuSuspect": true, "modele": "qwen3.5:4b",
                 "date": "2026-10-28T10:03:00Z", "champFutur": "ignoré"}""".formatted(s.candidatureId()));

        attendre(() -> analyse(s.candidatureId()).getStatut() == StatutAnalyse.TERMINEE);
        api.lire("/api/v1/entreprise/offres/{id}/candidatures", s.entreprise().token(), s.offreId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].analyse.statut").value("TERMINEE"))
                .andExpect(jsonPath("$.content[0].analyse.score").value(85))
                .andExpect(jsonPath("$.content[0].analyse.justification").value("Bon profil DevOps."))
                .andExpect(jsonPath("$.content[0].analyse.resume[1]").value("Stage chez OCP"))
                .andExpect(jsonPath("$.content[0].analyse.technologiesTrouvees[0]").value("Docker"))
                .andExpect(jsonPath("$.content[0].analyse.technologiesOffre").value(4))
                .andExpect(jsonPath("$.content[0].analyse.contenuSuspect").value(true))
                .andExpect(jsonPath("$.content[0].statut").value("EN_ATTENTE"));
        // L'étudiant ne voit ni le score ni l'analyse
        api.lire("/api/v1/etudiant/candidatures", s.etudiant().token())
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("score"))))
                .andExpect(content().string(not(containsString("analyse"))));
    }

    @Test
    void triParScore_auChoix_sansScoreALaFin_triParDefautParDate() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Compte entreprise = api.entreprise();
        Long offreId = api.offreOuverte(entreprise);
        Long premiere = postuler(api, offreId);
        Long deuxieme = postuler(api, offreId);
        Long troisieme = postuler(api, offreId);
        noter(premiere, 40);
        noter(troisieme, 90);
        // deuxieme : pas encore de score

        api.lire("/api/v1/entreprise/offres/{id}/candidatures?tri=score", entreprise.token(), offreId)
                .andExpect(jsonPath("$.content[*].id",
                        contains(troisieme.intValue(), premiere.intValue(), deuxieme.intValue())));
        // Sans paramètre : la plus récente d'abord, comme avant la phase 8
        api.lire("/api/v1/entreprise/offres/{id}/candidatures", entreprise.token(), offreId)
                .andExpect(jsonPath("$.content[*].id",
                        contains(troisieme.intValue(), deuxieme.intValue(), premiere.intValue())));
    }

    @Test
    void resultatOrphelin_candidatureSupprimee_ignoreSansErreur(CapturedOutput sortie) throws Exception {
        UUID id = UUID.randomUUID();

        publier(987_654_321L, id, """
                {"candidatureId": 987654321, "statut": "TERMINEE", "score": 70, "date": "2026-10-28T10:00:00Z"}""");

        // Traité sans exception : l'événement est inscrit (en cas d'erreur, la transaction l'annulerait)
        attendre(() -> evenementsTraites.existsById(id));
        attendre(() -> sortie.getOut().contains("Analyse de la candidature 987654321 (TERMINEE) ignorée"));
        assertThat(messagesDuTopic(TOPIC + ".DLT")).noneMatch(m -> id.toString().equals(entete(m)));
    }

    @Test
    void messageIllisible_versLeTopicDErreurs() throws Exception {
        UUID id = UUID.randomUUID();

        publier(5L, id, "{ ceci n'est pas du JSON");

        attendre(() -> messagesDuTopic(TOPIC + ".DLT").stream().anyMatch(m -> id.toString().equals(entete(m))));
        assertThat(evenementsTraites.existsById(id)).isFalse();
    }

    private Long postuler(ApiDeTest api, Long offreId) throws Exception {
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();
        return ApiDeTest.idDe(api.appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId),
                etudiant.token(), null).andExpect(status().isCreated()));
    }

    private void noter(Long candidatureId, int score) {
        Candidature candidature = candidatureRepository.findById(candidatureId).orElseThrow();
        candidature.getAnalyse().setStatut(StatutAnalyse.TERMINEE);
        candidature.getAnalyse().setScore(score);
        candidatureRepository.save(candidature);
    }

    private com.stagehub.candidature.entity.AnalyseIa analyse(Long candidatureId) {
        return candidatureRepository.findById(candidatureId).orElseThrow().getAnalyse();
    }

    private void publier(Long cle, String json) throws Exception {
        publier(cle, UUID.randomUUID(), json);
    }

    private void publier(Long cle, UUID id, String json) throws Exception {
        ProducerRecord<String, String> message = new ProducerRecord<>(TOPIC, String.valueOf(cle), json);
        message.headers().add("stagehub-evenement-id", id.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(message).get();
    }

    private static String entete(ConsumerRecord<String, String> message) {
        var entete = message.headers().lastHeader("stagehub-evenement-id");
        return entete == null ? null : new String(entete.value(), StandardCharsets.UTF_8);
    }

    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        Instant limite = Instant.now().plusSeconds(30);
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("condition non atteinte en 30 s").isBefore(limite);
            Thread.sleep(200);
        }
    }

    /** Messages du topic, lus depuis le début pendant 5 s. */
    private static List<ConsumerRecord<String, String>> messagesDuTopic(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consommateur = new KafkaConsumer<>(config)) {
            consommateur.subscribe(List.of(topic));
            Instant fin = Instant.now().plusSeconds(5);
            List<ConsumerRecord<String, String>> messages = new java.util.ArrayList<>();
            while (Instant.now().isBefore(fin)) {
                StreamSupport.stream(consommateur.poll(Duration.ofMillis(500)).spliterator(), false).forEach(messages::add);
            }
            return messages;
        }
    }
}
