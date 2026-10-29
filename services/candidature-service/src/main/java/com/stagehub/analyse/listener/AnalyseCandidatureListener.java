package com.stagehub.analyse.listener;

import com.stagehub.analyse.event.AnalyseCandidature;
import com.stagehub.analyse.event.EvenementInvalideException;
import com.stagehub.analyse.service.ResultatAnalyseService;
import com.stagehub.candidature.entity.StatutAnalyse;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.UUID;

/**
 * Point d'entrée Kafka (phase 8 e) : états de l'analyse publiés par ai-service. Lit l'id de l'événement (en-tête posé
 * par l'outbox) et le JSON, vérifie l'essentiel, puis délègue. Message illisible ou incomplet : topic « .DLT » sans
 * nouvel essai ; autre erreur : nouvelles tentatives (KafkaErreursConfig). Le traitement continue la trace reçue
 * (en-tête traceparent), celle de la candidature d'origine.
 */
@Component
public class AnalyseCandidatureListener {

    static final String ENTETE_ID_EVENEMENT = "stagehub-evenement-id";

    private final ResultatAnalyseService resultats;
    private final JsonMapper jsonMapper;

    public AnalyseCandidatureListener(ResultatAnalyseService resultats, JsonMapper jsonMapper) {
        this.resultats = resultats;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${stagehub.topics.analyse}")
    public void surAnalyse(@Payload String json, @Header(name = ENTETE_ID_EVENEMENT, required = false) String id) {
        AnalyseCandidature evenement = lire(json);
        if (evenement.candidatureId() == null || evenement.statut() == null
                || Arrays.stream(StatutAnalyse.values()).noneMatch(s -> s.name().equals(evenement.statut()))) {
            throw new EvenementInvalideException("Candidature ou statut d'analyse absent ou inconnu", null);
        }
        resultats.appliquer(idEvenement(id), evenement);
    }

    private AnalyseCandidature lire(String json) {
        try {
            return jsonMapper.readValue(json, AnalyseCandidature.class);
        } catch (JacksonException e) {
            throw new EvenementInvalideException("JSON illisible pour AnalyseCandidature", e);
        }
    }

    private static UUID idEvenement(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new EvenementInvalideException("Id d'événement absent ou invalide : " + id, e);
        }
    }
}
