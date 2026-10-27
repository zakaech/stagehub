package com.stagehub;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.stagehub.commons.security.JwtClaims;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

/**
 * Remplace auth-service dans les tests de candidature-service : une paire de clés RSA générée pour la durée des
 * tests signe des jetons au format d'auth-service, et le décodeur du service est remplacé par un décodeur qui fait
 * confiance à cette clé publique (au lieu d'aller chercher le JWKS d'auth-service sur le réseau).
 */
public final class JetonsDeTest {

    private static final RSAKey CLE = genererCle();
    private static final JwtEncoder ENCODEUR = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(CLE)));

    private JetonsDeTest() {
    }

    /** Jeton d'utilisateur : sub = email, claims uid et role, comme ceux d'auth-service. */
    public static String utilisateur(Long id, String email, String role) {
        Instant maintenant = Instant.now();
        return signer(JwtClaimsSet.builder()
                .issuer(JwtClaims.EMETTEUR)
                .subject(email)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plusSeconds(3600))
                .claim(JwtClaims.UTILISATEUR_ID, id)
                .claim(JwtClaims.ROLE, role)
                .build());
    }

    /** Jeton de service (appels internes d'auth-service). */
    public static String service() {
        return service("auth-service");
    }

    /** Jeton de service au nom d'un service précis (flux client credentials d'auth-service, phase 8 c). */
    public static String service(String sujet) {
        Instant maintenant = Instant.now();
        return signer(JwtClaimsSet.builder()
                .issuer(JwtClaims.EMETTEUR)
                .subject(sujet)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plusSeconds(300))
                .claim(JwtClaims.ROLE, JwtClaims.ROLE_SERVICE)
                .build());
    }

    private static String signer(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(CLE.getKeyID()).build();
        return ENCODEUR.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey genererCle() {
        try {
            return new RSAKeyGenerator(2048).keyID("cle-de-test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** À importer dans un contexte de test : prend le pas (@Primary) sur le décodeur JWKS de SecurityConfig. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        JwtDecoder jwtDecoderDeTest() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(CLE.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtClaims.EMETTEUR));
            return decoder;
        }
    }
}
