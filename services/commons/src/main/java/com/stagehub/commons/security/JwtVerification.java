package com.stagehub.commons.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Vérification des JWT par les services (et la gateway) : ils ne connaissent que la <b>clé publique</b>
 * d'auth-service, publiée au format JWKS. Ils peuvent vérifier un jeton, jamais en fabriquer.
 */
public final class JwtVerification {

    private JwtVerification() {
    }

    /**
     * Décodeur qui télécharge les clés publiques à l'adresse JWKS (au premier jeton reçu, puis en cache ; il les
     * recharge s'il voit un identifiant de clé « kid » inconnu, ce qui permet de changer de clé sans redémarrer).
     * Seul RS256 est accepté : un jeton qui annoncerait un autre algorithme (« none », HS256...) est refusé.
     * Vérifie aussi l'expiration (exp) et l'émetteur (iss).
     */
    public static JwtDecoder decodeurJwks(String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtClaims.EMETTEUR));
        return decoder;
    }

    /** Claim "role" (ex. "ENTREPRISE") → autorité "ROLE_ENTREPRISE", utilisée par hasRole(...). */
    public static JwtAuthenticationConverter convertisseurDeRoles() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
