package com.stagehub.commons.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Journal des requêtes (phase 8 a) : une ligne par requête reçue, avec la méthode, le chemin, le statut et la durée,
 * par exemple {@code POST /api/v1/etudiant/offres/3/candidatures → 201 en 245 ms}. Placé juste après le filtre
 * d'observation de Spring (ordre HIGHEST_PRECEDENCE + 1), il s'exécute dans la trace de la requête : la ligne porte
 * son traceId. Actif dans chaque service HTTP qui utilise commons (gateway, auth, offres, candidatures) : un appel
 * interne (Feign) y laisse aussi sa ligne, et une requête se suit de service en service avec un seul traceId.
 * <p>Ni paramètres, ni en-têtes, ni corps : rien qui puisse contenir un jeton ou une donnée personnelle. Les
 * healthchecks Docker (toutes les 10 s) ne sont pas journalisés.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JournalRequetesFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JournalRequetesFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long debut = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} → {} en {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                    (System.nanoTime() - debut) / 1_000_000);
        }
    }
}
