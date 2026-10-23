package com.stagehub.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 7 : le monolithe ne connecte plus personne (auth-service s'en charge) ; il vérifie les jetons avec la clé
 * publique d'auth-service. Il est derrière la gateway, qui a déjà vérifié le jeton et traité CORS : il le revérifie
 * quand même (défense en profondeur) et applique les règles fines (rôles, propriété).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemDetailHandler) throws Exception {
        http
                // API sans état authentifiée par en-tête Authorization : pas de cookie de session, donc pas de CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // Healthchecks Docker : seule la santé est exposée par Actuator (voir application.yml)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Appels d'autres services (création des profils étudiants par auth-service) : jeton de
                        // service seulement. Les offres publiques sont dans offre-service depuis la phase 7 (c).
                        .requestMatchers("/internal/**").hasRole(JwtClaims.ROLE_SERVICE)
                        .requestMatchers("/api/v1/etudiant/**").hasRole("ETUDIANT")
                        .requestMatchers("/api/v1/entreprise/**").hasRole("ENTREPRISE")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler));
        return http.build();
    }

    /**
     * Clés publiques d'auth-service (JWKS), téléchargées au premier jeton puis gardées en cache. Seul RS256 est
     * accepté ; expiration et émetteur vérifiés.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${stagehub.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtClaims.EMETTEUR));
        return decoder;
    }

    /** Transforme le claim "role" (ex. "ENTREPRISE") en autorité "ROLE_ENTREPRISE", utilisée par hasRole(...). */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
