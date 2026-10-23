package com.stagehub.outbox.service;

import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.event.CandidatureStatutChangeEvent;
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

        service.enregistrer(Topics.CANDIDATURE_STATUT_CHANGE, "7", new CandidatureStatutChangeEvent(7L, "sara@etu.ma",
                "Sara", "Stage Java", "Acme", StatutCandidature.EN_ATTENTE, StatutCandidature.ENTRETIEN));

        ArgumentCaptor<OutboxEvenement> ligne = ArgumentCaptor.forClass(OutboxEvenement.class);
        verify(outboxRepository).save(ligne.capture());
        assertThat(ligne.getValue().getId()).isNotNull();
        assertThat(ligne.getValue().getTopic()).isEqualTo(Topics.CANDIDATURE_STATUT_CHANGE);
        assertThat(ligne.getValue().getCle()).isEqualTo("7");
        assertThat(ligne.getValue().getType()).isEqualTo("CandidatureStatutChangeEvent");
        assertThat(ligne.getValue().getContenu())
                .isEqualTo("{\"candidatureId\":7,\"etudiantEmail\":\"sara@etu.ma\",\"etudiantPrenom\":\"Sara\","
                        + "\"offreTitre\":\"Stage Java\",\"entrepriseNom\":\"Acme\",\"ancienStatut\":\"EN_ATTENTE\","
                        + "\"nouveauStatut\":\"ENTRETIEN\"}");
        assertThat(ligne.getValue().getDateCreation()).isEqualTo(maintenant);
        assertThat(ligne.getValue().getDatePublication()).isNull();
    }
}
