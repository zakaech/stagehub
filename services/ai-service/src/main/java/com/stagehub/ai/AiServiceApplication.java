package com.stagehub.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * ai-service (phase 7 f : squelette ; phase 8 : Spring AI + Ollama). Service sans API : il reçoit par Kafka chaque
 * candidature créée et l'enregistre comme « demande d'analyse ». En phase 8, il calculera le score de matching et le
 * résumé du CV, puis renverra le résultat par un événement.
 */
@SpringBootApplication
@EnableFeignClients
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
