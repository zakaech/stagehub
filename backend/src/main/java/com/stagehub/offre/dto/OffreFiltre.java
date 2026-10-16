package com.stagehub.offre.dto;

import io.swagger.v3.oas.annotations.Parameter;

import java.util.List;

/**
 * Critères de recherche des offres, lus dans les paramètres de requête
 * ({@code ?ville=Rabat&domaine=Data&technologies=Java&technologies=SQL}). Tous facultatifs.
 */
public record OffreFiltre(
        @Parameter(description = "Ville, insensible à la casse") String ville,
        @Parameter(description = "Domaine, insensible à la casse") String domaine,
        @Parameter(description = "Au moins une de ces technologies (insensible à la casse)") List<String> technologies
) {

    public OffreFiltre {
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
