package com.stagehub.candidature.client;

import com.stagehub.candidature.dto.OffreResumeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/**
 * Lecture des offres dans offre-service (OpenFeign : Spring génère l'implémentation et trouve les instances dans
 * Eureka). Chaque appel passe par un circuit breaker Resilience4j ; en cas d'échec, {@link OffreClientRepli} décide
 * de la réponse (erreur claire ou mode dégradé).
 */
@FeignClient(name = "offre-service", contextId = "offres", path = "/internal/offres",
        configuration = RelaisJetonFeignConfig.class, fallbackFactory = OffreClientRepli.class)
public interface OffreClient {

    @GetMapping("/{id}")
    OffreInterne offre(@PathVariable("id") Long id);

    /** Un seul appel pour toute une page de candidatures. */
    @GetMapping
    List<OffreResumeResponse> resumes(@RequestParam("ids") Collection<Long> ids);
}
