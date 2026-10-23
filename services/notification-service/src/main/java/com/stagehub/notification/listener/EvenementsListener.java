package com.stagehub.notification.listener;

import com.stagehub.notification.event.CandidatureStatutChange;
import com.stagehub.notification.event.EntrepriseValidation;
import com.stagehub.notification.event.EvenementInvalideException;
import com.stagehub.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.UUID;

/**
 * Point d'entrée Kafka : lit l'id de l'événement (en-tête posé par l'outbox du producteur) et le JSON, puis délègue.
 * Si le traitement lève une exception, le message n'est pas validé : il est rejoué (voir KafkaErreursConfig).
 */
@Component
public class EvenementsListener {

    static final String ENTETE_ID_EVENEMENT = "stagehub-evenement-id";

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    public EvenementsListener(NotificationService notificationService, JsonMapper jsonMapper) {
        this.notificationService = notificationService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${stagehub.topics.candidature-statut-change}")
    public void surChangementStatutCandidature(@Payload String json,
                                               @Header(name = ENTETE_ID_EVENEMENT, required = false) String id) {
        CandidatureStatutChange evenement = lire(json, CandidatureStatutChange.class);
        exiger(evenement.etudiantEmail(), evenement.nouveauStatut());
        notificationService.surChangementStatutCandidature(idEvenement(id), evenement);
    }

    @KafkaListener(topics = "${stagehub.topics.entreprise-validation}")
    public void surValidationEntreprise(@Payload String json,
                                        @Header(name = ENTETE_ID_EVENEMENT, required = false) String id) {
        EntrepriseValidation evenement = lire(json, EntrepriseValidation.class);
        exiger(evenement.email(), evenement.decision());
        notificationService.surValidationEntreprise(idEvenement(id), evenement);
    }

    private <T> T lire(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new EvenementInvalideException("JSON illisible pour " + type.getSimpleName(), e);
        }
    }

    private static UUID idEvenement(String id) {
        try {
            return UUID.fromString(Objects.requireNonNull(id, "en-tête " + ENTETE_ID_EVENEMENT + " absent"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new EvenementInvalideException("Id d'événement absent ou invalide : " + id, e);
        }
    }

    private static void exiger(String... champs) {
        for (String champ : champs) {
            if (champ == null || champ.isBlank()) {
                throw new EvenementInvalideException("Champ obligatoire absent dans l'événement", null);
            }
        }
    }
}
