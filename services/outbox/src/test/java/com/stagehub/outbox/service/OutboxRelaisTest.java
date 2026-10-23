package com.stagehub.outbox.service;

import com.stagehub.outbox.OutboxEntetes;
import com.stagehub.outbox.entity.OutboxEvenement;
import com.stagehub.outbox.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelaisTest {

    private static final String TOPIC = "stagehub.test.evenements";
    private static final Instant MAINTENANT = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelais relais;

    @BeforeEach
    void creer() {
        relais = new OutboxRelais(outboxRepository, kafkaTemplate, Duration.ofSeconds(1),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    void publieChaqueEvenementPuisLeMarque() {
        OutboxEvenement a = evenement("1");
        OutboxEvenement b = evenement("2");
        when(outboxRepository.verrouillerAPublier(OutboxRelais.TAILLE_LOT)).thenReturn(List.of(a, b));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(accuse());

        relais.publier();

        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        assertThat(a.getDatePublication()).isEqualTo(MAINTENANT);
        assertThat(b.getDatePublication()).isEqualTo(MAINTENANT);
    }

    @Test
    void kafkaEnPanne_seArreteAuPremierEchec_lesSuivantsRestentAPublier() {
        OutboxEvenement a = evenement("1");
        OutboxEvenement b = evenement("2");
        OutboxEvenement c = evenement("3");
        when(outboxRepository.verrouillerAPublier(OutboxRelais.TAILLE_LOT)).thenReturn(List.of(a, b, c));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(accuse())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker injoignable")));

        relais.publier();

        assertThat(a.getDatePublication()).isEqualTo(MAINTENANT);
        assertThat(b.getDatePublication()).isNull();
        assertThat(c.getDatePublication()).isNull();
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void apresUnePanne_lePassageSuivantPublieLesEvenementsEnAttente() {
        OutboxEvenement a = evenement("1");
        when(outboxRepository.verrouillerAPublier(OutboxRelais.TAILLE_LOT)).thenReturn(List.of(a));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker injoignable")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("toujours injoignable")))
                .thenReturn(accuse());

        relais.publier();
        relais.publier();
        assertThat(a.getDatePublication()).isNull();

        relais.publier();
        assertThat(a.getDatePublication()).isEqualTo(MAINTENANT);
    }

    @Test
    void enregistrementKafka_cleEtEnTetes() {
        OutboxEvenement evenement = evenement("42");

        ProducerRecord<String, String> enregistrement = OutboxRelais.enregistrementKafka(evenement);

        assertThat(enregistrement.topic()).isEqualTo(TOPIC);
        assertThat(enregistrement.key()).isEqualTo("42");
        assertThat(enregistrement.value()).isEqualTo("{\"candidatureId\":42}");
        assertThat(new String(enregistrement.headers().lastHeader(OutboxEntetes.ID_EVENEMENT).value(), StandardCharsets.UTF_8))
                .isEqualTo(evenement.getId().toString());
        assertThat(new String(enregistrement.headers().lastHeader(OutboxEntetes.TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("CandidatureStatutChangeEvent");
    }

    @Test
    void purge_supprimeLesEvenementsPubliesDepuisPlusDUneSemaine() {
        relais.purger();

        verify(outboxRepository).supprimerPubliesAvant(MAINTENANT.minus(Duration.ofDays(7)));
    }

    private static CompletableFuture<SendResult<String, String>> accuse() {
        return CompletableFuture.completedFuture(null);
    }

    private static OutboxEvenement evenement(String cle) {
        OutboxEvenement e = new OutboxEvenement();
        e.setId(UUID.randomUUID());
        e.setTopic(TOPIC);
        e.setCle(cle);
        e.setType("CandidatureStatutChangeEvent");
        e.setContenu("{\"candidatureId\":" + cle + "}");
        e.setDateCreation(MAINTENANT.minusSeconds(5));
        return e;
    }
}
