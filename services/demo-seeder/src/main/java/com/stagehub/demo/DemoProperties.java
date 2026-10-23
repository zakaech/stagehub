package com.stagehub.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param apiUrl          adresse de la gateway (ex. http://api-gateway:8080)
 * @param motDePasse      mot de passe commun des comptes de démo (DEMO_PASSWORD : données publiques, pas un secret)
 * @param adminEmail      compte admin (créé par auth-service au démarrage), pour valider les entreprises
 * @param adminPassword   son mot de passe
 * @param attenteMax      temps laissé aux services pour être joignables à travers la gateway
 */
@ConfigurationProperties("stagehub.demo")
public record DemoProperties(String apiUrl, String motDePasse, String adminEmail, String adminPassword,
                             Duration attenteMax) {

    public DemoProperties {
        if (attenteMax == null) {
            attenteMax = Duration.ofMinutes(3);
        }
    }
}
