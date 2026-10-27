package com.stagehub.ai.analyse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * API interne d'offre-service (phase 8 d) : contenu de l'offre à comparer au CV. Même jeton de service que pour
 * candidature-service (ajouté par Spring Cloud OpenFeign), annuaire Eureka, traceparent.
 */
@FeignClient(name = "offre-service", contextId = "offres")
public interface OffreClient {

    @GetMapping("/internal/offres/{id}")
    OffreAAnalyser offre(@PathVariable("id") Long offreId);

    /** Ce que l'analyse lit dans la réponse d'offre-service (les autres champs sont ignorés). */
    record OffreAAnalyser(Long id, String titre, String description, String domaine, String ville,
                          String entrepriseNom, List<String> technologies) {

        public List<String> technologies() {
            return technologies == null ? List.of() : technologies;
        }
    }
}
