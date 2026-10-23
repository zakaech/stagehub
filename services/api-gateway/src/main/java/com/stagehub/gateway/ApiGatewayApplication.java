package com.stagehub.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Point d'entrée unique de l'API (Nginx → gateway → services). Elle vérifie le JWT et les grands droits
 * par préfixe d'URL, traite CORS, puis relaie la requête vers l'instance trouvée dans Eureka.
 * Les routes sont déclarées dans application.yml ; les services revalident le jeton (défense en profondeur).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
