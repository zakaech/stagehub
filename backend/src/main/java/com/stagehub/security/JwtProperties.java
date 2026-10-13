package com.stagehub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Configuration du JWT ({@code stagehub.jwt.*}). Le constructeur compact valide le secret :
 * s'il fait moins de 32 octets, le binding échoue et l'application refuse de démarrer.
 */
@ConfigurationProperties("stagehub.jwt")
public record JwtProperties(String secret, Duration expiration, String issuer) {

    /** HS256 = HMAC-SHA256 : une clé de moins de 256 bits affaiblit la signature. */
    public static final int TAILLE_MIN_SECRET_OCTETS = 32;

    public JwtProperties {
        int taille = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (taille < TAILLE_MIN_SECRET_OCTETS) {
            throw new IllegalArgumentException(
                    "JWT_SECRET doit faire au moins %d octets (256 bits) pour HS256 ; longueur actuelle : %d octets"
                            .formatted(TAILLE_MIN_SECRET_OCTETS, taille));
        }
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "stagehub";
        }
    }

    public byte[] secretEnOctets() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
