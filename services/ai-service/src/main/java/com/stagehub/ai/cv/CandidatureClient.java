package com.stagehub.ai.cv;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * API interne de candidature-service, trouvé dans l'annuaire Eureka. Chaque appel porte un jeton de
 * service au nom d'ai-service (ajouté par Spring Cloud OpenFeign : spring.cloud.openfeign.oauth2), seul jeton accepté
 * par cet endpoint.
 */
@FeignClient(name = "candidature-service", contextId = "candidatures")
public interface CandidatureClient {

    /** CV figé de la candidature (celui que l'entreprise télécharge), en PDF. */
    @GetMapping("/internal/candidatures/{id}/cv")
    byte[] cv(@PathVariable("id") Long candidatureId);
}
