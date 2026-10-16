package com.stagehub.offre.controller;

import com.stagehub.common.dto.PageResponse;
import com.stagehub.offre.dto.FiltresDisponiblesResponse;
import com.stagehub.offre.dto.OffreFiltre;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.service.OffreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultation publique des offres (sans authentification) : uniquement les offres OUVERTE. */
@RestController
@RequestMapping("/api/v1/offres")
@Tag(name = "Offres (public)")
@SecurityRequirements
public class OffreController {

    private final OffreService offreService;

    public OffreController(OffreService offreService) {
        this.offreService = offreService;
    }

    @GetMapping
    @Operation(summary = "Recherche paginée des offres ouvertes",
            description = "Filtres facultatifs, cumulés (ET) : ville et domaine (égalité insensible à la casse), "
                    + "technologies (l'offre doit en demander au moins une).")
    public PageResponse<OffreResponse> rechercher(
            @ParameterObject OffreFiltre filtre,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return offreService.rechercherOffresOuvertes(filtre, pageable);
    }

    @GetMapping("/filtres")
    @Operation(summary = "Valeurs disponibles pour les filtres (villes, domaines, technologies des offres ouvertes)")
    public FiltresDisponiblesResponse filtres() {
        return offreService.filtresDisponibles();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une offre ouverte")
    public OffreResponse detail(@PathVariable Long id) {
        return offreService.getOffreOuverte(id);
    }
}
