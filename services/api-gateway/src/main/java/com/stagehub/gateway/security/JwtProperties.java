package com.stagehub.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Vérification des JWT ({@code stagehub.jwt.*}). Sous-étape (a) : le monolithe signe en HS256, la gateway
 * vérifie avec le même secret ; à partir de la sous-étape (b), elle vérifiera avec la clé publique d'auth-service.
 * Un secret de moins de 32 octets empêche le démarrage, comme dans le monolithe.
 */
@ConfigurationProperties("stagehub.jwt")
public record JwtProperties(String secret, String issuer) {

    public static final int TAILLE_MIN_SECRET_OCTETS = 32;

    public JwtProperties {
        int taille = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (taille < TAILLE_MIN_SECRET_OCTETS) {
            throw new IllegalArgumentException(
                    "JWT_SECRET doit faire au moins %d octets (256 bits) pour HS256 ; longueur actuelle : %d octets"
                            .formatted(TAILLE_MIN_SECRET_OCTETS, taille));
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "stagehub";
        }
    }

    public byte[] secretEnOctets() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
