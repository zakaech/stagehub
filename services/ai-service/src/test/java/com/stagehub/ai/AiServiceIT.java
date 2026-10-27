package com.stagehub.ai;

import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import com.stagehub.ai.repository.EvenementTraiteRepository;
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
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** ai-service de bout en bout, sur un vrai Kafka et un vrai PostgreSQL (Testcontainers, voir IntegrationTestBase). */
@ExtendWith(OutputCaptureExtension.class)
class AiServiceIT extends IntegrationTestBase {

    private static final String TOPIC = "stagehub.candidature.creee";
    private static final AtomicLong IDS = new AtomicLong(1_000);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private DemandeAnalyseRepository demandeAnalyseRepository;
    @Autowired
    private EvenementTraiteRepository evenementTraiteRepository;

    @Test
    void candidatureCreee_demandeDAnalyseEnregistree() throws Exception {
        long candidatureId = IDS.incrementAndGet();
        publier(candidatureId, UUID.randomUUID(), candidature(candidatureId, "\"cv-fige.pdf\""));

        attendre(() -> demandeAnalyseRepository.existsById(candidatureId));
        var demande = demandeAnalyseRepository.findById(candidatureId).orElseThrow();
        assertThat(demande.getOffreId()).isEqualTo(3L);
        assertThat(demande.getCvFichier()).isEqualTo("cv-fige.pdf");
        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
    }

    /**
     * Phase 8 (a) : le message porte le contexte de trace de la candidature (posé par l'outbox de candidature-service).
     * Le traitement le continue (même traceId dans les logs) et la demande le conserve pour l'analyse.
     */
    @Test
    void candidatureCreee_traceContinueeDansLesLogsEtConservee(CapturedOutput sortie) throws Exception {
        long candidatureId = IDS.incrementAndGet();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e" + String.format("%04x", candidatureId % 0x10000);
        String traceparent = "00-" + traceId + "-00f067aa0ba902b7-01";
        publier(candidatureId, UUID.randomUUID(), candidature(candidatureId, "\"cv.pdf\""), traceparent);

        attendre(() -> demandeAnalyseRepository.existsById(candidatureId));
        assertThat(demandeAnalyseRepository.findById(candidatureId).orElseThrow().getTraceparent()).isEqualTo(traceparent);
        // La ligne est écrite avant le commit, donc avant que la demande soit visible : pas de course ici, mais on
        // attend quand même la sortie, comme dans les autres tests qui lisent les logs
        attendre(() -> sortie.getOut().matches("(?s).*" + traceId + ".*Candidature " + candidatureId + " enregistrée pour analyse.*"));
    }

    @Test
    void memeEvenementRecuDeuxFois_uneSeuleDemande() throws Exception {
        long candidatureId = IDS.incrementAndGet();
        UUID id = UUID.randomUUID();
        // Ce que produit le relais de l'outbox s'il s'arrête entre l'envoi et le marquage : le même événement, deux fois
        publier(candidatureId, id, candidature(candidatureId, "null"));
        publier(candidatureId, id, candidature(candidatureId, "null"));

        attendre(() -> demandeAnalyseRepository.existsById(candidatureId));
        Thread.sleep(2000);
        // Le second message a été lu et ignoré (sinon : violation de la clé primaire, nouvelles tentatives puis DLT)
        assertThat(evenementTraiteRepository.existsById(id)).isTrue();
        assertThat(messagesDuTopic(TOPIC + ".DLT")).noneMatch(r -> id.toString().equals(entete(r)));
    }

    @Test
    void messageIllisible_versLeTopicDErreurs_sansDemande() throws Exception {
        UUID id = UUID.randomUUID();
        publier(IDS.incrementAndGet(), id, "{ ceci n'est pas du JSON");

        attendre(() -> messagesDuTopic(TOPIC + ".DLT").stream().anyMatch(r -> id.toString().equals(entete(r))));
        assertThat(evenementTraiteRepository.existsById(id)).isFalse();
    }

    private void publier(long cle, UUID id, String json) throws Exception {
        publier(cle, id, json, null);
    }

    private void publier(long cle, UUID id, String json, String traceparent) throws Exception {
        ProducerRecord<String, String> message = new ProducerRecord<>(TOPIC, String.valueOf(cle), json);
        message.headers().add("stagehub-evenement-id", id.toString().getBytes(StandardCharsets.UTF_8));
        if (traceparent != null) {
            message.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(message).get();
    }

    private static String candidature(long candidatureId, String cvJson) {
        return """
                {"candidatureId":%d,"offreId":3,"etudiantId":5,"cvFichier":%s,"champFutur":"ignoré"}
                """.formatted(candidatureId, cvJson);
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

    /**
     * Messages du topic, lus depuis le début pendant 5 s (le premier poll d'un nouveau consommateur revient souvent
     * vide, le temps que Kafka lui attribue les partitions).
     */
    private static List<ConsumerRecord<String, String>> messagesDuTopic(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> messages = new ArrayList<>();
        try (KafkaConsumer<String, String> consommateur = new KafkaConsumer<>(config)) {
            consommateur.subscribe(List.of(topic));
            Instant fin = Instant.now().plusSeconds(5);
            while (Instant.now().isBefore(fin)) {
                consommateur.poll(Duration.ofMillis(500)).forEach(messages::add);
            }
        }
        return messages;
    }
}
