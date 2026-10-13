package com.stagehub.admin.controller;

import com.stagehub.admin.dto.StatistiquesResponse;
import com.stagehub.admin.service.StatistiquesService;
import com.stagehub.common.dto.PageResponse;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.dto.ValidationEntrepriseRequest;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.service.EntrepriseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {

    private final EntrepriseService entrepriseService;
    private final StatistiquesService statistiquesService;

    public AdminController(EntrepriseService entrepriseService, StatistiquesService statistiquesService) {
        this.entrepriseService = entrepriseService;
        this.statistiquesService = statistiquesService;
    }

    @GetMapping("/entreprises")
    @Operation(summary = "Entreprises, filtrables par statut de validation (ex. EN_ATTENTE)")
    public PageResponse<EntrepriseResponse> entreprises(
            @RequestParam(required = false) StatutValidation statut,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return entrepriseService.lister(statut, pageable);
    }

    @PatchMapping("/entreprises/{id}/validation")
    @Operation(summary = "Valider ou refuser une entreprise en attente")
    public EntrepriseResponse statuer(@PathVariable Long id, @Valid @RequestBody ValidationEntrepriseRequest request) {
        return entrepriseService.statuer(id, request.decision());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales de la plateforme")
    public StatistiquesResponse statistiques() {
        return statistiquesService.calculer();
    }
}
