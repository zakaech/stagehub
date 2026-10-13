package com.stagehub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origines autorisées à appeler l'API depuis un navigateur ({@code CORS_ALLOWED_ORIGINS}, séparées par des virgules).
 * En dev, Angular passe par le proxy de {@code ng serve} (même origine) : CORS n'est alors pas sollicité,
 * mais reste nécessaire dès que le frontend est servi depuis une autre origine que l'API.
 */
@ConfigurationProperties("stagehub.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
