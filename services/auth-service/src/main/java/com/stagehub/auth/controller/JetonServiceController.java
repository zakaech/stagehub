package com.stagehub.auth.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stagehub.auth.security.ClientsService;
import com.stagehub.auth.security.JwtService;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Point d'accès OAuth2 des jetons de service (RFC 6749, §4.4 « client credentials »). Un service
 * s'authentifie avec {@code Authorization: Basic base64(identifiant:secret)} et {@code grant_type=client_credentials} ;
 * il reçoit un jeton court (5 min) signé par auth-service : sujet = son identifiant, rôle SERVICE.
 * <p>Format de réponse et d'erreur du standard (et non ProblemDetail) : c'est le client OAuth2 de Spring Security, dans
 * le service appelant, qui le lit. La gateway ne route pas ce chemin : il n'est joignable qu'entre services.
 */
@RestController
@Hidden
public class JetonServiceController {

    private static final Logger log = LoggerFactory.getLogger(JetonServiceController.class);
    private static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";

    private final ClientsService clientsService;
    private final JwtService jwtService;

    public JetonServiceController(ClientsService clientsService, JwtService jwtService) {
        this.clientsService = clientsService;
        this.jwtService = jwtService;
    }

    public record JetonServiceResponse(@JsonProperty("access_token") String accessToken,
                                       @JsonProperty("token_type") String tokenType,
                                       @JsonProperty("expires_in") long expiresIn) {
    }

    @PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> jeton(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestParam(name = "grant_type", required = false) String grantType) {
        String[] identifiants = identifiantsBasic(authorization);
        if (identifiants == null || !clientsService.authentifier(identifiants[0], identifiants[1])) {
            // Ni le nom proposé ni le secret ne sont journalisés : un secret mal recopié finirait dans les logs
            log.warn("Demande de jeton de service refusée : client inconnu ou secret incorrect");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"stagehub\"")
                    .body(Map.of("error", "invalid_client"));
        }
        if (!GRANT_CLIENT_CREDENTIALS.equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        JwtService.JetonDeService jeton = jwtService.jetonDeService(identifiants[0]);
        log.info("Jeton de service délivré à {}", identifiants[0]);
        return ResponseEntity.ok()
                // RFC 6749 §5.1 : une réponse qui contient un jeton n'est mise en cache nulle part
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new JetonServiceResponse(jeton.valeur(), "Bearer", jeton.duree().toSeconds()));
    }

    /**
     * {@code Basic base64(identifiant:secret)}, chacun encodé comme dans un formulaire avant le Base64 (RFC 6749,
     * §2.3.1 : c'est ce que fait le client de Spring Security). Null si l'en-tête est absent ou mal formé.
     */
    private static String[] identifiantsBasic(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String decode = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
            int separateur = decode.indexOf(':');
            if (separateur < 0) {
                return null;
            }
            return new String[]{
                    URLDecoder.decode(decode.substring(0, separateur), StandardCharsets.UTF_8),
                    URLDecoder.decode(decode.substring(separateur + 1), StandardCharsets.UTF_8)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
