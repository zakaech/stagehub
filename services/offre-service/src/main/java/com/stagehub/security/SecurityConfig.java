package com.stagehub.security;

import com.stagehub.commons.security.JwtClaims;
import com.stagehub.commons.security.JwtVerification;
import com.stagehub.commons.security.ProblemDetailSecurityHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Vérifie les jetons avec la clé publique d'auth-service (défense en profondeur : la gateway l'a déjà fait) et
 * applique les mêmes règles que le monolithe pour les routes reprises. CORS : traité par la gateway.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemDetailHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Création des profils entreprise par auth-service (inscription) : jeton de service seulement
                        .requestMatchers("/internal/entreprises/**").hasRole(JwtClaims.ROLE_SERVICE)
                        // Lecture des offres par le service des candidatures, avec le jeton de l'utilisateur relayé
                        .requestMatchers(HttpMethod.GET, "/internal/offres", "/internal/offres/**").authenticated()
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/offres", "/api/v1/offres/**").permitAll()
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

    @Bean
    JwtDecoder jwtDecoder(@Value("${stagehub.jwt.jwk-set-uri}") String jwkSetUri) {
        return JwtVerification.decodeurJwks(jwkSetUri);
    }
}
