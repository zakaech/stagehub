package com.stagehub.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Service des comptes : inscription, connexion, émission des JWT signés en RS256 avec la clé privée (que lui seul
 * possède) et publication de la clé publique (JWKS) pour la gateway et les autres services.
 * Les profils (étudiant, entreprise) vivent dans le service métier correspondant : l'inscription les y crée par
 * OpenFeign, avec compensation en cas d'échec (saga).
 * scanBasePackages : inclut aussi com.stagehub.commons (gestion des erreurs de sécurité).
 */
@SpringBootApplication(scanBasePackages = "com.stagehub")
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
