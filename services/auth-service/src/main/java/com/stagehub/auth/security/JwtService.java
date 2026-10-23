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
import java.time.Instant;

/** Fabrique les JWT : mêmes claims que le monolithe (sub = email, uid, role), signés en RS256 avec un « kid ». */
@Service
public class JwtService {

    static final String SUJET_SERVICE = "auth-service";

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
        Instant maintenant = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtClaims.EMETTEUR)
                .subject(SUJET_SERVICE)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(properties.dureeJetonService()))
                .claim(JwtClaims.ROLE, JwtClaims.ROLE_SERVICE)
                .build();
        return signer(claims);
    }

    private String signer(JwtClaimsSet claims) {
        // Le kid dans l'en-tête indique aux vérificateurs quelle clé publique du JWKS utiliser
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
