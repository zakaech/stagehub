package com.stagehub.outbox;

import com.stagehub.ApiDeTest;
import com.stagehub.FauxOffreService;
import com.stagehub.IntegrationTestBase;
import com.stagehub.common.Topics;
import com.stagehub.outbox.entity.OutboxEvenement;
import com.stagehub.outbox.repository.OutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Outbox de bout en bout, avec un vrai Kafka (Testcontainers) : l'événement est écrit dans la transaction de l'action,
 * puis publié par le relais avec sa clé et son id ; une action refusée (rollback) n'en produit aucun.
 */
@TestPropertySource(properties = {
        "stagehub.outbox.relais.actif=true",
        "stagehub.outbox.relais.intervalle=200ms"
})
class OutboxIT extends IntegrationTestBase {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    static {
        KAFKA.start();
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void changementDeStatut_publieDansKafkaAvecLaCleEtLIdDeLEvenement() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();

        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ENTRETIEN").andExpect(status().isOk());

        ConsumerRecord<String, String> message = attendreMessage(Topics.CANDIDATURE_STATUT_CHANGE, s.candidatureId().toString());
        assertThat(message.value())
                .contains("\"nouveauStatut\":\"ENTRETIEN\"")
                .contains("\"ancienStatut\":\"EN_ATTENTE\"")
                .contains("\"etudiantEmail\":\"" + s.etudiant().email() + "\"");
        String id = new String(message.headers().lastHeader(OutboxEntetes.ID_EVENEMENT).value(), StandardCharsets.UTF_8);
        // La ligne de l'outbox correspondante est marquée publiée
        OutboxEvenement ligne = outboxRepository.findById(UUID.fromString(id)).orElseThrow();
        attendre(() -> outboxRepository.findById(ligne.getId()).orElseThrow().getDatePublication() != null);
    }

    @Test
    void candidature_publieCandidatureCreeePourAiService() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);

        ApiDeTest.Scenario s = api.scenarioAvecCandidature();

        ConsumerRecord<String, String> message = attendreMessage(Topics.CANDIDATURE_CREEE, s.candidatureId().toString());
        assertThat(message.value())
                .contains("\"candidatureId\":" + s.candidatureId())
                .contains("\"offreId\":" + s.offreId())
                .contains("\"etudiantId\":" + s.etudiant().id());
        assertThat(message.headers().lastHeader(OutboxEntetes.ID_EVENEMENT)).isNotNull();
    }

    /**
     * Un seul traceId, de la requête de l'étudiant jusqu'au message que lira ai-service, en passant par
     * l'appel Feign vers offre-service. Le relais publie depuis un autre thread : sans le traceparent enregistré dans
     * l'outbox, la trace s'arrêterait là.
     */
    @Test
    void candidature_memeTraceIdPourOffreServiceEtPourLeMessageKafka() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        Long offreId = api.offreOuverte(api.entreprise());
        ApiDeTest.Compte etudiant = api.inscrireEtudiant();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        Long candidatureId = ApiDeTest.idDe(api.appeler(post("/api/v1/etudiant/offres/{id}/candidatures", offreId)
                        .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01"), etudiant.token(), null)
                .andExpect(status().isCreated()));

        FauxOffreService.SERVEUR.verify(getRequestedFor(urlPathEqualTo("/internal/offres/" + offreId))
                .withHeader("traceparent", matching("00-" + traceId + "-[0-9a-f]{16}-0[13]")));
        ConsumerRecord<String, String> message = attendreMessage(Topics.CANDIDATURE_CREEE, candidatureId.toString());
        assertThat(new String(message.headers().lastHeader(OutboxEntetes.TRACEPARENT).value(), StandardCharsets.UTF_8))
                .startsWith("00-" + traceId + "-");
    }

    @Test
    void transitionRefusee_rollback_aucunEvenementDansLOutbox() throws Exception {
        ApiDeTest api = new ApiDeTest(mockMvc);
        ApiDeTest.Scenario s = api.scenarioAvecCandidature();
        long avant = evenementsDe(s.candidatureId());

        // EN_ATTENTE → ACCEPTEE est interdit : 409, la transaction est annulée, l'outbox avec
        api.changerStatutCandidature(s.candidatureId(), s.entreprise().token(), "ACCEPTEE").andExpect(status().isConflict());

        assertThat(evenementsDe(s.candidatureId())).isEqualTo(avant).isZero();
    }

    private long evenementsDe(Long candidatureId) {
        return outboxRepository.findAll().stream()
                .filter(e -> e.getTopic().equals(Topics.CANDIDATURE_STATUT_CHANGE) && e.getCle().equals(candidatureId.toString()))
                .count();
    }

    /** Lit le topic depuis le début jusqu'à trouver le message de cette clé (au plus 30 s). */
    private static ConsumerRecord<String, String> attendreMessage(String topic, String cle) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consommateur = new KafkaConsumer<>(config)) {
            consommateur.subscribe(List.of(topic));
            Instant limite = Instant.now().plusSeconds(30);
            while (Instant.now().isBefore(limite)) {
                Optional<ConsumerRecord<String, String>> trouve = java.util.stream.StreamSupport
                        .stream(consommateur.poll(Duration.ofMillis(500)).spliterator(), false)
                        .filter(r -> cle.equals(r.key()))
                        .findFirst();
                if (trouve.isPresent()) {
                    return trouve.get();
                }
            }
        }
        throw new AssertionError("Aucun message de clé " + cle + " dans " + topic);
    }

    private static void attendre(java.util.function.BooleanSupplier condition) throws InterruptedException {
        Instant limite = Instant.now().plusSeconds(10);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(limite)) {
                throw new AssertionError("Condition non atteinte en 10 s");
            }
            Thread.sleep(200);
        }
    }
}
