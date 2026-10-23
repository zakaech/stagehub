package com.stagehub.admin.controller;

import com.stagehub.admin.dto.StatistiquesOffresResponse;
import com.stagehub.admin.service.StatistiquesOffresService;
import com.stagehub.commons.dto.PageResponse;
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

/** Administration des entreprises et statistiques des offres (rôle ADMIN, cf. SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {

    private final EntrepriseService entrepriseService;
    private final StatistiquesOffresService statistiquesService;

    public AdminController(EntrepriseService entrepriseService, StatistiquesOffresService statistiquesService) {
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

    @GetMapping("/stats/offres")
    @Operation(summary = "Statistiques des offres et des entreprises")
    public StatistiquesOffresResponse statistiques() {
        return statistiquesService.calculer();
    }
}
