package com.stagehub.auth.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.stagehub.auth.dto.TokenResponse;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.commons.security.JwtClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Fabrique les JWT : mêmes claims que le monolithe (sub = email, uid, role), signés en RS256 avec un « kid ». */
@Service
public class JwtService {

    static final String SUJET_SERVICE = "auth-service";

    /** Jeton de service et sa durée de validité (renvoyée au client OAuth2 dans {@code expires_in}). */
    public record JetonDeService(String valeur, Duration duree) {
    }

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final String kid;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties, RSAKey cleDeSignature, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.kid = cleDeSignature.getKeyID();
        this.clock = clock;
    }

    public TokenResponse genererToken(Utilisateur utilisateur) {
        Instant maintenant = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.EMETTEUR)
                .subject(utilisateur.getEmail())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(properties.expiration()))
                .claim(JwtClaims.UTILISATEUR_ID, utilisateur.getId())
                .claim(JwtClaims.ROLE, utilisateur.getRole().name())
                .build();
        return new TokenResponse(signer(claims), "Bearer", properties.expiration().toSeconds(), utilisateur.getRole());
    }

    /**
     * Jeton court pour les appels internes faits sans utilisateur (inscription, nettoyage) : rôle SERVICE, aucun uid.
     * Aucun compte ne peut avoir ce rôle (absent de l'enum Role) : seul auth-service, avec sa clé privée, l'émet.
     */
    public String jetonDeService() {
        return jetonDeService(SUJET_SERVICE).valeur();
    }

    /**
     * Même jeton, pour un autre service authentifié par le flux client credentials (phase 8 c) : son identifiant
     * devient le sujet, ce qui permet à un service appelé de n'accepter qu'un appelant précis (ex. ai-service).
     */
    public JetonDeService jetonDeService(String sujet) {
        Instant maintenant = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.EMETTEUR)
                .subject(sujet)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(properties.dureeJetonService()))
                .claim(JwtClaims.ROLE, JwtClaims.ROLE_SERVICE)
                .build();
        return new JetonDeService(signer(claims), properties.dureeJetonService());
    }

    private String signer(JwtClaimsSet claims) {
        // Le kid dans l'en-tête indique aux vérificateurs quelle clé publique du JWKS utiliser
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
