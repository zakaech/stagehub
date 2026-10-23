package com.stagehub.outbox.service;

import com.stagehub.outbox.Topics;
import com.stagehub.outbox.entity.OutboxEvenement;
import com.stagehub.outbox.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relais de l'outbox : toutes les secondes, publie dans Kafka les événements pas encore publiés, puis les marque.
 * <ul>
 *   <li>on attend l'accusé de réception de Kafka (acks=all) <b>avant</b> de marquer : un événement marqué est sûr
 *       d'être dans Kafka ;</li>
 *   <li>si le service s'arrête entre l'envoi et le marquage, l'événement repartira : livraison « au moins une
 *       fois », d'où l'en-tête d'id qui permet au consommateur d'ignorer un doublon ;</li>
 *   <li>Kafka indisponible : on s'arrête au premier échec, les suivants restent dans la table (dans l'ordre) et
 *       repartent au prochain passage. Le monolithe continue de fonctionner sans Kafka.</li>
 * </ul>
 * Désactivable ({@code stagehub.outbox.relais.actif=false}) : la plupart des tests n'ont pas de Kafka.
 */
@Component
@ConditionalOnProperty(name = "stagehub.outbox.relais.actif", havingValue = "true", matchIfMissing = true)
public class OutboxRelais {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelais.class);
    static final int TAILLE_LOT = 100;
    static final Duration CONSERVATION = Duration.ofDays(7);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration attenteAccuse;
    private final Clock clock;
    private boolean kafkaEnPanne;

    public OutboxRelais(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${stagehub.outbox.relais.attente-accuse:10s}") Duration attenteAccuse, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.attenteAccuse = attenteAccuse;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${stagehub.outbox.relais.intervalle:1s}")
    @Transactional
    public void publier() {
        List<OutboxEvenement> lot = outboxRepository.verrouillerAPublier(TAILLE_LOT);
        for (OutboxEvenement evenement : lot) {
            try {
                kafkaTemplate.send(enregistrementKafka(evenement)).get(attenteAccuse.toMillis(), TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                signalerPanne(e);
                // Les événements déjà publiés de ce lot sont marqués (commit) ; les autres attendent, dans l'ordre
                return;
            }
            evenement.setDatePublication(clock.instant());
        }
        if (kafkaEnPanne && !lot.isEmpty()) {
            kafkaEnPanne = false;
            log.info("Kafka de nouveau joignable : publication des événements en attente reprise");
        }
    }

    /** Les événements publiés ne sont gardés qu'une semaine (diagnostic), puis supprimés chaque nuit. */
    @Scheduled(cron = "0 30 3 * * *", zone = "${stagehub.fuseau}")
    @Transactional
    public void purger() {
        int supprimes = outboxRepository.supprimerPubliesAvant(clock.instant().minus(CONSERVATION));
        log.info("Outbox : {} événement(s) publié(s) depuis plus de {} jours supprimé(s)", supprimes, CONSERVATION.toDays());
    }

    static ProducerRecord<String, String> enregistrementKafka(OutboxEvenement evenement) {
        ProducerRecord<String, String> enregistrement =
                new ProducerRecord<>(evenement.getTopic(), evenement.getCle(), evenement.getContenu());
        enregistrement.headers().add(Topics.ENTETE_ID_EVENEMENT, evenement.getId().toString().getBytes(StandardCharsets.UTF_8));
        enregistrement.headers().add(Topics.ENTETE_TYPE, evenement.getType().getBytes(StandardCharsets.UTF_8));
        return enregistrement;
    }

    /** Un seul avertissement par panne (le relais réessaie chaque seconde : pas de journal inondé). */
    private void signalerPanne(Exception e) {
        if (!kafkaEnPanne) {
            kafkaEnPanne = true;
            log.warn("Kafka indisponible ({}) : les événements restent dans l'outbox et seront publiés plus tard",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        } else {
            log.debug("Kafka toujours indisponible", e);
        }
    }
}
