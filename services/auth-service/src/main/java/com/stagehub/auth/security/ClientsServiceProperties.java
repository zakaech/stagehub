package com.stagehub.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Services autorisés à demander un jeton à auth-service ({@code stagehub.clients-service.*}, phase 8 c) : flux OAuth2
 * « client credentials ». Chaque service s'authentifie avec son identifiant et son propre secret (variable
 * d'environnement, jamais dans le dépôt) ; le jeton obtenu porte son nom comme sujet, ce qui permet à un service
 * appelé de n'accepter qu'un appelant précis.
 *
 * @param secrets identifiant du client (ex. ai-service) → secret partagé avec ce seul service
 */
@ConfigurationProperties("stagehub.clients-service")
public record ClientsServiceProperties(Map<String, String> secrets) {

    public ClientsServiceProperties {
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }
}
