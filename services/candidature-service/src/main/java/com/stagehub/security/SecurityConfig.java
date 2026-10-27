package com.stagehub.security;

import com.stagehub.commons.security.JwtClaims;
import com.stagehub.commons.security.JwtVerification;
import com.stagehub.commons.security.ProblemDetailSecurityHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Sécurité de candidature-service : chaque requête porte un JWT signé par auth-service (RS256), revérifié ici même
 * s'il est déjà passé par la gateway (défense en profondeur).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Seul service autorisé à lire le CV d'une candidature (analyse, phase 8 c) : sujet de son jeton de service. */
    static final String SUJET_AI_SERVICE = "ai-service";

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
                        // CV d'une candidature : réservé à ai-service. Le rôle SERVICE ne suffit pas (auth-service l'a
                        // aussi) : le sujet du jeton doit être « ai-service » (jeton obtenu par client credentials)
                        .requestMatchers(HttpMethod.GET, "/internal/candidatures/*/cv").access(seulementAiService())
                        // Création des profils étudiants par auth-service (inscription) : jeton de service seulement
                        .requestMatchers("/internal/**").hasRole(JwtClaims.ROLE_SERVICE)
                        .requestMatchers("/api/v1/etudiant/**").hasRole("ETUDIANT")
                        .requestMatchers("/api/v1/entreprise/**").hasRole("ENTREPRISE")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtVerification.convertisseurDeRoles()))
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler));
        return http.build();
    }

    /** Rôle SERVICE et sujet « ai-service » : les deux sont signés par auth-service, donc infalsifiables. */
    private static AuthorizationManager<RequestAuthorizationContext> seulementAiService() {
        AuthorizationManager<RequestAuthorizationContext> sujetAiService = (authentification, contexte) ->
                new AuthorizationDecision(SUJET_AI_SERVICE.equals(authentification.get().getName()));
        return AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasRole(JwtClaims.ROLE_SERVICE), sujetAiService);
    }

    /** Clés publiques d'auth-service (JWKS) ; RS256 seul, expiration et émetteur vérifiés (voir JwtVerification). */
    @Bean
    JwtDecoder jwtDecoder(@Value("${stagehub.jwt.jwk-set-uri}") String jwkSetUri) {
        return JwtVerification.decodeurJwks(jwkSetUri);
    }
}
