package com.stagehub.security;

import com.stagehub.commons.security.JwtClaims;
import com.stagehub.commons.security.JwtVerification;
import com.stagehub.commons.security.ProblemDetailSecurityHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité de candidature-service : chaque requête porte un JWT signé par auth-service (RS256), revérifié ici même
 * s'il est déjà passé par la gateway (défense en profondeur).
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

    /** Clés publiques d'auth-service (JWKS) ; RS256 seul, expiration et émetteur vérifiés (voir JwtVerification). */
    @Bean
    JwtDecoder jwtDecoder(@Value("${stagehub.jwt.jwk-set-uri}") String jwkSetUri) {
        return JwtVerification.decodeurJwks(jwkSetUri);
    }
}
