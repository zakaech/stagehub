package com.stagehub.outbox.service;

import com.stagehub.outbox.entity.OutboxEvenement;
import com.stagehub.outbox.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

/**
 * Enregistre un événement à publier, <b>dans la transaction de l'action métier</b> (MANDATORY : appelé hors
 * transaction, il échoue). C'est ce qui règle le problème du « dual write » : on n'écrit plus dans deux systèmes
 * (PostgreSQL puis Kafka) sans transaction commune, mais dans un seul ; Kafka est alimenté ensuite par OutboxRelais.
 * Le contexte de trace de l'action est enregistré avec l'événement, pour que son traitement par le
 * consommateur garde le même traceId.
 */
@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final ContexteDeTrace contexteDeTrace;

    public OutboxService(OutboxRepository outboxRepository, JsonMapper jsonMapper, Clock clock,
                         ContexteDeTrace contexteDeTrace) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.contexteDeTrace = contexteDeTrace;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(String topic, String cle, Object evenement) {
        OutboxEvenement ligne = new OutboxEvenement();
        ligne.setId(UUID.randomUUID());
        ligne.setTopic(topic);
        ligne.setCle(cle);
        ligne.setType(evenement.getClass().getSimpleName());
        ligne.setContenu(jsonMapper.writeValueAsString(evenement));
        ligne.setDateCreation(clock.instant());
        ligne.setTraceparent(contexteDeTrace.traceparentCourant());
        outboxRepository.save(ligne);
    }
}
