package com.stagehub.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Signature des JWT ({@code stagehub.jwt.*}).
 *
 * @param privateKey        clé privée RSA au format PKCS#8, encodée en Base64 (sur une ligne : variable JWT_PRIVATE_KEY).
 *                          Génération : {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0}
 * @param fichierCle        à défaut de clé fournie : fichier où la clé est générée au premier démarrage puis relue
 *                          (JWT_KEY_FILE ; volume Docker en local). Sur un serveur, fournir plutôt privateKey.
 * @param keyId             identifiant de la clé (« kid ») ; vide = empreinte calculée de la clé publique (RFC 7638)
 * @param expiration        durée de validité des jetons des utilisateurs
 * @param dureeJetonService durée de validité des jetons de service (appels internes)
 */
@ConfigurationProperties("stagehub.jwt")
public record JwtProperties(String privateKey, Path fichierCle, String keyId, Duration expiration,
                            Duration dureeJetonService) {

    public JwtProperties {
        if (fichierCle != null && fichierCle.toString().isBlank()) {
            fichierCle = null;
        }
        if ((privateKey == null || privateKey.isBlank()) && fichierCle == null) {
            throw new IllegalArgumentException("Clé de signature absente : définir JWT_PRIVATE_KEY (clé privée RSA PKCS#8 "
                    + "en Base64, ex. openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0) "
                    + "ou JWT_KEY_FILE (fichier où générer la clé au premier démarrage)");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
        if (dureeJetonService == null) {
            dureeJetonService = Duration.ofMinutes(5);
        }
    }

    public boolean cleFournie() {
        return privateKey != null && !privateKey.isBlank();
    }
}
