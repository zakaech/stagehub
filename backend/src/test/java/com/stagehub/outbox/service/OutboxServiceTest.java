package com.stagehub.outbox.service;

import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.event.EntrepriseValidationEvent;
import com.stagehub.outbox.Topics;
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

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Test
    void enregistrer_serialiseLEvenementEnJsonAvecSonTopicEtSaCle() {
        Instant maintenant = Instant.parse("2026-10-23T10:00:00Z");
        OutboxService service = new OutboxService(outboxRepository, JsonMapper.builder().build(),
                Clock.fixed(maintenant, ZoneOffset.UTC));

        service.enregistrer(Topics.ENTREPRISE_VALIDATION, "7",
                new EntrepriseValidationEvent(7L, "rh@acme.ma", "Acme", StatutValidation.VALIDEE));

        ArgumentCaptor<OutboxEvenement> ligne = ArgumentCaptor.forClass(OutboxEvenement.class);
        verify(outboxRepository).save(ligne.capture());
        assertThat(ligne.getValue().getId()).isNotNull();
        assertThat(ligne.getValue().getTopic()).isEqualTo(Topics.ENTREPRISE_VALIDATION);
        assertThat(ligne.getValue().getCle()).isEqualTo("7");
        assertThat(ligne.getValue().getType()).isEqualTo("EntrepriseValidationEvent");
        assertThat(ligne.getValue().getContenu())
                .isEqualTo("{\"entrepriseId\":7,\"email\":\"rh@acme.ma\",\"nom\":\"Acme\",\"decision\":\"VALIDEE\"}");
        assertThat(ligne.getValue().getDateCreation()).isEqualTo(maintenant);
        assertThat(ligne.getValue().getDatePublication()).isNull();
    }
}
