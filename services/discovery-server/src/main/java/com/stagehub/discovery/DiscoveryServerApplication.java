package com.stagehub.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Annuaire Eureka. Au démarrage, chaque service y déclare son nom (ex. « offre-service ») et son adresse,
 * puis envoie un signe de vie régulier. La gateway lit cet annuaire pour savoir où envoyer une requête
 * {@code lb://offre-service}, et répartit la charge s'il y a plusieurs instances.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
