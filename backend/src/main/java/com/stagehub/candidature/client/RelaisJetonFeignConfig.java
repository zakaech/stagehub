package com.stagehub.candidature.client;

import feign.RequestInterceptor;
import feign.Retryer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Les appels vers offre-service sont faits au nom de l'utilisateur de la requête en cours : on relaie son jeton
 * (offre-service le revérifie). Pas de @Configuration : cette configuration ne vaut que pour OffreClient.
 */
public class RelaisJetonFeignConfig {

    /**
     * Une seule nouvelle tentative (après 100 ms), uniquement sur erreur réseau ou délai dépassé (jamais sur une
     * réponse HTTP d'erreur) ; le LoadBalancer peut alors choisir une autre instance. Tous les appels d'OffreClient
     * sont des lectures (GET) : les rejouer est sans risque. Une écriture ne serait jamais rejouée ainsi.
     */
    @Bean
    Retryer nouvelleTentativeSurErreurReseau() {
        return new Retryer.Default(100, 1000, 2);
    }

    @Bean
    RequestInterceptor relaisDuJeton() {
        return requete -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
                HttpServletRequest entrante = attributs.getRequest();
                String jeton = entrante.getHeader(HttpHeaders.AUTHORIZATION);
                if (jeton != null) {
                    requete.header(HttpHeaders.AUTHORIZATION, jeton);
                }
            }
        };
    }
}
