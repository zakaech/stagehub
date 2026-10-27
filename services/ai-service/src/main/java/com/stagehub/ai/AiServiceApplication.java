package com.stagehub.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * ai-service (phase 7 f : squelette ; phase 8 : Spring AI + Ollama). Service sans API : il reçoit par Kafka chaque
 * candidature créée et l'enregistre comme « demande d'analyse » ; une tâche planifiée l'analyse (score de matching,
 * résumé du CV) et publie le résultat par un événement.
 * <p>La bibliothèque outbox (composants, entité, repository) est hors du package de cette classe : ses packages sont
 * ajoutés explicitement à ceux que Spring parcourt.
 */
@SpringBootApplication(scanBasePackages = {"com.stagehub.ai", "com.stagehub.outbox"})
@EntityScan(basePackages = {"com.stagehub.ai", "com.stagehub.outbox"})
@EnableJpaRepositories(basePackages = {"com.stagehub.ai", "com.stagehub.outbox"})
@ConfigurationPropertiesScan
@EnableFeignClients
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
