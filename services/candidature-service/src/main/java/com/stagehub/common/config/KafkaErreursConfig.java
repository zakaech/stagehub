package com.stagehub.common.config;

import com.stagehub.analyse.event.EvenementInvalideException;
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
 * Messages reçus en échec (même règle que notification-service et ai-service) : quelques nouvelles
 * tentatives espacées (base de données momentanément indisponible...), puis le topic « .DLT », même partition.
 * Un message illisible part tout de suite en « .DLT » : le rejouer ne le rendrait pas lisible.
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
