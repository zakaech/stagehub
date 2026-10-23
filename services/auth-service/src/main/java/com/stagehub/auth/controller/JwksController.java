package com.stagehub.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Clé(s) publique(s) au format JWKS (RFC 7517), lue(s) par la gateway et les services pour vérifier les jetons.
 * {@code toPublicJWK()} : seuls le module et l'exposant publics sortent, jamais la clé privée (testé).
 * Joignable dans le réseau Docker seulement : la gateway n'a pas de route vers cette adresse.
 */
@RestController
@Hidden
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey cleDeSignature) {
        this.jwks = new JWKSet(cleDeSignature.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
