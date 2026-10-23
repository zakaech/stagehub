package com.stagehub.auth.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.stagehub.commons.security.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Signature asymétrique RS256 : la <b>clé privée</b> (secrète, seulement ici) signe ; la <b>clé publique</b>,
 * calculée à partir de la clé privée et publiée sur /.well-known/jwks.json, permet à tous de vérifier.
 * Une clé invalide ou trop courte empêche le démarrage.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);
    static final int TAILLE_MIN_BITS = 2048;

    @Bean
    RSAKey cleDeSignature(JwtProperties properties) throws GeneralSecurityException, JOSEException, IOException {
        String base64 = properties.cleFournie() ? properties.privateKey() : lireOuGenerer(properties.fichierCle());
        RSAPrivateCrtKey privee = lireClePrivee(base64);
        if (privee.getModulus().bitLength() < TAILLE_MIN_BITS) {
            throw new IllegalArgumentException("JWT_PRIVATE_KEY : clé RSA de %d bits, %d minimum"
                    .formatted(privee.getModulus().bitLength(), TAILLE_MIN_BITS));
        }
        // Une clé privée RSA « CRT » contient le module et l'exposant public : la clé publique s'en déduit
        RSAPublicKey publique = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(privee.getModulus(), privee.getPublicExponent()));
        RSAKey cle = new RSAKey.Builder(publique)
                .privateKey(privee)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        String kid = properties.keyId() == null || properties.keyId().isBlank()
                ? cle.computeThumbprint().toString()
                : properties.keyId();
        return new RSAKey.Builder(cle).keyID(kid).build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey cleDeSignature) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(cleDeSignature)));
    }

    /** auth-service vérifie ses propres jetons (/auth/me) avec sa clé publique, sans appel réseau. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey cleDeSignature) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(cleDeSignature.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtClaims.EMETTEUR));
        return decoder;
    }

    /**
     * Sans clé fournie (essai local en une commande) : la clé est générée au premier démarrage dans un fichier (volume
     * Docker), puis relue aux démarrages suivants — les jetons restent valides après un redémarrage, et aucune clé
     * n'est jamais dans le dépôt. Création exclusive : si deux instances démarrent ensemble, une seule écrit, l'autre
     * relit (prévu pour l'essai local à une instance ; sur un serveur, la clé est fournie par JWT_PRIVATE_KEY).
     */
    static String lireOuGenerer(Path fichier) throws GeneralSecurityException, IOException {
        if (Files.exists(fichier)) {
            return Files.readString(fichier).trim();
        }
        KeyPairGenerator generateur = KeyPairGenerator.getInstance("RSA");
        generateur.initialize(TAILLE_MIN_BITS);
        String base64 = Base64.getEncoder().encodeToString(generateur.generateKeyPair().getPrivate().getEncoded());
        if (fichier.getParent() != null) {
            Files.createDirectories(fichier.getParent());
        }
        try {
            creerLisibleParLeProprietaireSeul(fichier);
        } catch (FileAlreadyExistsException e) {
            return Files.readString(fichier).trim();
        }
        Files.writeString(fichier, base64, StandardOpenOption.WRITE);
        log.warn("Aucune clé JWT fournie (JWT_PRIVATE_KEY) : clé RSA générée et enregistrée dans {}. "
                + "Sur un serveur, fournir la clé par JWT_PRIVATE_KEY (sauvegarde, plusieurs instances).", fichier);
        return base64;
    }

    /** Clé privée : droits rw------- (Linux, conteneur) ; sous Windows (tests), les droits POSIX n'existent pas. */
    private static void creerLisibleParLeProprietaireSeul(Path fichier) throws IOException {
        try {
            Files.createFile(fichier, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            Files.createFile(fichier);
        }
    }

    static RSAPrivateCrtKey lireClePrivee(String valeur) throws GeneralSecurityException {
        // Accepte aussi un PEM collé tel quel : en-têtes et retours à la ligne retirés
        String base64 = valeur.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JWT_PRIVATE_KEY n'est pas du Base64 valide", e);
        }
        try {
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException e) {
            // Piège courant : « openssl genpkey -outform DER » (OpenSSL 3) et les « RSA PRIVATE KEY » sont en PKCS#1
            throw new IllegalArgumentException("JWT_PRIVATE_KEY n'est pas une clé RSA au format PKCS#8 (clé PKCS#1 ?). "
                    + "Conversion : openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0", e);
        }
    }
}
