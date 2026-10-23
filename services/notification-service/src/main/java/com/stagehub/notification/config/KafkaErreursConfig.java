package com.stagehub.notification.config;

import com.stagehub.notification.event.EvenementInvalideException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;

/**
 * Que faire d'un message dont le traitement échoue ?
 * <ul>
 *   <li>erreur passagère (SMTP indisponible...) : nouvelles tentatives, espacées de plus en plus (2 s, 4 s, 8 s) ;</li>
 *   <li>toujours en échec, ou message illisible (sans nouvel essai : le relire ne changera rien) : il est publié tel
 *       quel dans le <b>topic d'erreurs</b> (« dead letter topic », même nom + {@code .DLT}), avec la cause en
 *       en-tête, et la lecture continue. Un message défectueux ne bloque pas les suivants ; on peut l'examiner et le
 *       rejouer plus tard.</li>
 * </ul>
 * Spring Boot branche automatiquement ce bean sur les listeners {@code @KafkaListener}.
 */
@Configuration
public class KafkaErreursConfig {

    @Bean
    DefaultErrorHandler gestionnaireErreurs(KafkaTemplate<?, ?> kafkaTemplate,
                                            @Value("${stagehub.erreurs.tentatives}") int tentatives,
                                            @Value("${stagehub.erreurs.premier-delai}") Duration premierDelai) {
        DeadLetterPublishingRecoverer versTopicDErreurs = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (message, erreur) -> new TopicPartition(message.topic() + ".DLT", message.partition()));
        ExponentialBackOff attente = new ExponentialBackOff(premierDelai.toMillis(), 2.0);
        attente.setMaxAttempts(tentatives);
        DefaultErrorHandler gestionnaire = new DefaultErrorHandler(versTopicDErreurs, attente);
        gestionnaire.addNotRetryableExceptions(EvenementInvalideException.class);
        return gestionnaire;
    }
}
