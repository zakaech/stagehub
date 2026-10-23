package com.stagehub.notification;

import com.stagehub.notification.repository.EvenementTraiteRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * notification-service avec un vrai Kafka et un vrai PostgreSQL (Testcontainers) ; seul l'envoi SMTP est simulé.
 * Messages construits comme ceux du relais de l'outbox : clé, JSON, en-tête d'id d'événement.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "stagehub.mail.url-frontend=http://front",
        // Nouvelles tentatives rapides en test
        "stagehub.erreurs.tentatives=2",
        "stagehub.erreurs.premier-delai=200ms"
})
class NotificationIT {

    private static final String TOPIC_CANDIDATURES = "stagehub.candidature.statut-change";
    private static final String TOPIC_ENTREPRISES = "stagehub.entreprise.validation";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @MockitoBean
    private JavaMailSender mailSender;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private EvenementTraiteRepository evenementTraiteRepository;

    @Test
    void changementDeStatut_emailEnvoyeALEtudiant() throws Exception {
        String email = destinataire();
        publier(TOPIC_CANDIDATURES, "1", UUID.randomUUID(), candidature(email, "ACCEPTEE"));

        verify(mailSender, timeout(20_000)).send(argThat(pour(email, "Candidature acceptée")));
    }

    @Test
    void validationEntreprise_emailEnvoyeALEntreprise() throws Exception {
        String email = destinataire();
        publier(TOPIC_ENTREPRISES, "2", UUID.randomUUID(),
                "{\"entrepriseId\":2,\"email\":\"%s\",\"nom\":\"Acme\",\"decision\":\"VALIDEE\"}".formatted(email));

        verify(mailSender, timeout(20_000)).send(argThat(pour(email, "validé")));
    }

    @Test
    void memeEvenementRecuDeuxFois_unSeulEmail() throws Exception {
        String email = destinataire();
        UUID id = UUID.randomUUID();
        // Ce que produit le relais s'il s'arrête entre l'envoi et le marquage : le même événement, deux fois
        publier(TOPIC_CANDIDATURES, "3", id, candidature(email, "ENTRETIEN"));
        publier(TOPIC_CANDIDATURES, "3", id, candidature(email, "ENTRETIEN"));

        verify(mailSender, timeout(20_000)).send(argThat(pour(email, "Entretien")));
        Thread.sleep(3000);
        verify(mailSender, times(1)).send(argThat(pour(email, "")));
        assertThat(evenementTraiteRepository.existsById(id)).isTrue();
    }

    @Test
    void messageIllisible_versLeTopicDErreurs_sansEmailNiNouvelEssai() throws Exception {
        UUID id = UUID.randomUUID();
        publier(TOPIC_CANDIDATURES, "4", id, "{ ceci n'est pas du JSON");

        ConsumerRecord<String, String> enErreur = attendreMessage(TOPIC_CANDIDATURES + ".DLT", id);
        assertThat(enErreur.value()).isEqualTo("{ ceci n'est pas du JSON");
        assertThat(entete(enErreur, "kafka_dlt-exception-cause-fqcn")).contains("EvenementInvalideException");
        assertThat(evenementTraiteRepository.existsById(id)).isFalse();
    }

    @Test
    void smtpEnPanne_nouvellesTentativesPuisTopicDErreurs_evenementPasMarque() throws Exception {
        String email = destinataire();
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(argThat(pour(email, "")));
        UUID id = UUID.randomUUID();

        publier(TOPIC_CANDIDATURES, "5", id, candidature(email, "REFUSEE"));

        ConsumerRecord<String, String> enErreur = attendreMessage(TOPIC_CANDIDATURES + ".DLT", id);
        assertThat(entete(enErreur, "kafka_dlt-exception-cause-fqcn")).contains("MailSendException");
        // 1 essai + 2 nouvelles tentatives ; l'inscription de l'événement a été annulée à chaque fois
        verify(mailSender, times(3)).send(argThat(pour(email, "")));
        assertThat(evenementTraiteRepository.existsById(id)).isFalse();
    }

    @Test
    void enTeteDIdAbsent_versLeTopicDErreurs() throws Exception {
        String email = destinataire();
        kafkaTemplate.send(TOPIC_CANDIDATURES, "6", candidature(email, "ENTRETIEN")).get();

        // Sans id, impossible de garantir « une seule fois » : le message est mis de côté, pas traité
        ConsumerRecord<String, String> enErreur = attendreMessage(TOPIC_CANDIDATURES + ".DLT", r -> r.value().contains(email));
        assertThat(entete(enErreur, "kafka_dlt-exception-cause-fqcn")).contains("EvenementInvalideException");
        verify(mailSender, never()).send(argThat(pour(email, "")));
    }

    private void publier(String topic, String cle, UUID id, String json) throws Exception {
        ProducerRecord<String, String> message = new ProducerRecord<>(topic, cle, json);
        message.headers().add("stagehub-evenement-id", id.toString().getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(message).get();
    }

    private static String candidature(String email, String statut) {
        return """
                {"candidatureId":1,"etudiantEmail":"%s","etudiantPrenom":"Sara","offreTitre":"Stage Java",
                 "entrepriseNom":"Acme","ancienStatut":"EN_ATTENTE","nouveauStatut":"%s","champFutur":"ignoré"}
                """.formatted(email, statut);
    }

    private static String destinataire() {
        return "etu-" + UUID.randomUUID().toString().substring(0, 8) + "@notification.test";
    }

    private static ArgumentMatcher<SimpleMailMessage> pour(String email, String dansLObjet) {
        return m -> m != null && m.getTo() != null && m.getTo()[0].equals(email)
                && m.getSubject() != null && m.getSubject().contains(dansLObjet);
    }

    private static String entete(ConsumerRecord<String, String> message, String nom) {
        return new String(message.headers().lastHeader(nom).value(), StandardCharsets.UTF_8);
    }

    private static ConsumerRecord<String, String> attendreMessage(String topic, UUID id) {
        return attendreMessage(topic, r -> r.headers().lastHeader("stagehub-evenement-id") != null
                && id.toString().equals(entete(r, "stagehub-evenement-id")));
    }

    /** Lit le topic depuis le début jusqu'à trouver un message qui convient (au plus 30 s). */
    private static ConsumerRecord<String, String> attendreMessage(String topic,
                                                                  java.util.function.Predicate<ConsumerRecord<String, String>> critere) {
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
                Optional<ConsumerRecord<String, String>> trouve = StreamSupport
                        .stream(consommateur.poll(Duration.ofMillis(500)).spliterator(), false)
                        .filter(critere)
                        .findFirst();
                if (trouve.isPresent()) {
                    return trouve.get();
                }
            }
        }
        throw new AssertionError("Aucun message attendu dans " + topic);
    }
}
