package com.stagehub.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Origines autorisées par CORS ({@code stagehub.cors.allowed-origins}, liste séparée par des virgules). */
@ConfigurationProperties("stagehub.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
