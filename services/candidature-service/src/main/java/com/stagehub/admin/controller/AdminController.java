package com.stagehub.admin.controller;

import com.stagehub.admin.dto.StatistiquesCandidaturesResponse;
import com.stagehub.admin.service.StatistiquesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statistiques des candidatures (rôle ADMIN). La validation des entreprises et les
 * statistiques des offres sont dans offre-service.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {

    private final StatistiquesService statistiquesService;

    public AdminController(StatistiquesService statistiquesService) {
        this.statistiquesService = statistiquesService;
    }

    @GetMapping("/stats/candidatures")
    @Operation(summary = "Statistiques des candidatures (total et répartition par statut)")
    public StatistiquesCandidaturesResponse statistiques() {
        return statistiquesService.calculer();
    }
}
