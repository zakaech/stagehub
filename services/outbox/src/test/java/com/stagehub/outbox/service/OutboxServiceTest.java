package com.stagehub.outbox.service;

import com.stagehub.outbox.entity.OutboxEvenement;
import com.stagehub.outbox.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    enum Decision { VALIDEE }

    record EvenementDeTest(Long entrepriseId, String email, String nom, Decision decision) {
    }

    private static final Instant MAINTENANT = Instant.parse("2026-10-23T10:00:00Z");
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private ContexteDeTrace contexteDeTrace;

    private OutboxService service() {
        return new OutboxService(outboxRepository, JsonMapper.builder().build(), Clock.fixed(MAINTENANT, ZoneOffset.UTC),
                contexteDeTrace);
    }

    @Test
    void enregistrer_serialiseLEvenementEnJsonAvecSonTopicEtSaCle() {
        OutboxService service = service();

        service.enregistrer("stagehub.test.evenements", "7",
                new EvenementDeTest(7L, "rh@acme.ma", "Acme", Decision.VALIDEE));

        ArgumentCaptor<OutboxEvenement> ligne = ArgumentCaptor.forClass(OutboxEvenement.class);
        verify(outboxRepository).save(ligne.capture());
        assertThat(ligne.getValue().getId()).isNotNull();
        assertThat(ligne.getValue().getTopic()).isEqualTo("stagehub.test.evenements");
        assertThat(ligne.getValue().getCle()).isEqualTo("7");
        assertThat(ligne.getValue().getType()).isEqualTo("EvenementDeTest");
        assertThat(ligne.getValue().getContenu())
                .isEqualTo("{\"entrepriseId\":7,\"email\":\"rh@acme.ma\",\"nom\":\"Acme\",\"decision\":\"VALIDEE\"}");
        assertThat(ligne.getValue().getDateCreation()).isEqualTo(MAINTENANT);
        assertThat(ligne.getValue().getDatePublication()).isNull();
        assertThat(ligne.getValue().getTraceparent()).isNull();
    }

    @Test
    void enregistrer_conserveLeContexteDeTraceDeLAction() {
        when(contexteDeTrace.traceparentCourant()).thenReturn(TRACEPARENT);

        service().enregistrer("stagehub.test.evenements", "7",
                new EvenementDeTest(7L, "rh@acme.ma", "Acme", Decision.VALIDEE));

        ArgumentCaptor<OutboxEvenement> ligne = ArgumentCaptor.forClass(OutboxEvenement.class);
        verify(outboxRepository).save(ligne.capture());
        assertThat(ligne.getValue().getTraceparent()).isEqualTo(TRACEPARENT);
    }
}
