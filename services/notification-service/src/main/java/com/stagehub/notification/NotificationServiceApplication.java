package com.stagehub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Service sans API : il écoute les topics Kafka des événements métier (changement de statut d'une candidature,
 * validation d'une entreprise) et envoie les emails correspondants. Il ne connaît ni la base ni le code des services
 * qui publient : seulement le contrat JSON des messages.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
