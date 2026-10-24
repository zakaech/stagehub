package com.stagehub.ai.listener;

import com.stagehub.ai.event.CandidatureCreee;
import com.stagehub.ai.event.EvenementInvalideException;
import com.stagehub.ai.service.DemandeAnalyseService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Point d'entrée Kafka : lit l'id de l'événement (en-tête posé par l'outbox de candidature-service) et le JSON, puis
 * délègue. Si le traitement lève une exception, le message est rejoué (voir KafkaErreursConfig).
 */
@Component
public class CandidatureCreeeListener {

    static final String ENTETE_ID_EVENEMENT = "stagehub-evenement-id";

    private final DemandeAnalyseService demandeAnalyseService;
    private final JsonMapper jsonMapper;

    public CandidatureCreeeListener(DemandeAnalyseService demandeAnalyseService, JsonMapper jsonMapper) {
        this.demandeAnalyseService = demandeAnalyseService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${stagehub.topics.candidature-creee}")
    public void surCandidatureCreee(@Payload String json,
                                    @Header(name = ENTETE_ID_EVENEMENT, required = false) String id) {
        CandidatureCreee evenement = lire(json);
        if (evenement.candidatureId() == null || evenement.offreId() == null || evenement.etudiantId() == null) {
            throw new EvenementInvalideException("Champ obligatoire absent dans l'événement", null);
        }
        demandeAnalyseService.surCandidatureCreee(idEvenement(id), evenement);
    }

    private CandidatureCreee lire(String json) {
        try {
            return jsonMapper.readValue(json, CandidatureCreee.class);
        } catch (JacksonException e) {
            throw new EvenementInvalideException("JSON illisible pour CandidatureCreee", e);
        }
    }

    private static UUID idEvenement(String id) {
        try {
            return UUID.fromString(Objects.requireNonNull(id, "en-tête " + ENTETE_ID_EVENEMENT + " absent"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new EvenementInvalideException("Id d'événement absent ou invalide : " + id, e);
        }
    }
}
