package com.stagehub.auth.client;

import com.stagehub.auth.security.JwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chaque appel Feign d'auth-service est un appel interne sans utilisateur (inscription, nettoyage) : on y joint un
 * jeton de service court (rôle SERVICE), signé avec la clé privée. Les endpoints /internal/** des autres services
 * n'acceptent que ce rôle ; la gateway, elle, refuse /internal/** à tout le monde.
 */
@Configuration
public class JetonDeServiceFeignConfig {

    @Bean
    RequestInterceptor jetonDeService(JwtService jwtService) {
        return requete -> requete.header("Authorization", "Bearer " + jwtService.jetonDeService());
    }
}
