package com.stagehub.security;

import com.stagehub.auth.dto.TokenResponse;
import com.stagehub.auth.entity.Utilisateur;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse genererToken(Utilisateur utilisateur) {
        Instant maintenant = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(utilisateur.getEmail())
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(properties.expiration()))
                .claim(JwtClaims.UTILISATEUR_ID, utilisateur.getId())
                .claim(JwtClaims.ROLE, utilisateur.getRole().name())
                .build();

        // L'en-tête doit annoncer HS256 explicitement (l'encodeur suppose RS256 par défaut)
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(token, "Bearer", properties.expiration().toSeconds(), utilisateur.getRole());
    }
}
