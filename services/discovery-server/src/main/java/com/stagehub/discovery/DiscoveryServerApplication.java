package com.stagehub.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Annuaire Eureka. Au démarrage, chaque service y déclare son nom (ex. « stagehub-backend ») et son adresse,
 * puis envoie un signe de vie régulier. La gateway lit cet annuaire pour savoir où envoyer une requête
 * {@code lb://stagehub-backend}, et répartit la charge s'il y a plusieurs instances.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
