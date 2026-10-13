package com.stagehub.candidature.controller;

import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.ChangementStatutCandidatureRequest;
import com.stagehub.candidature.service.CandidatureService;
import com.stagehub.common.dto.PageResponse;
import com.stagehub.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entreprise")
@Tag(name = "Espace entreprise — candidatures")
public class EntrepriseCandidatureController {

    private final CandidatureService candidatureService;

    public EntrepriseCandidatureController(CandidatureService candidatureService) {
        this.candidatureService = candidatureService;
    }

    @GetMapping("/offres/{offreId}/candidatures")
    @Operation(summary = "Candidatures reçues pour une de mes offres")
    public PageResponse<CandidatureEntrepriseResponse> candidaturesDeLOffre(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long offreId,
            @ParameterObject @PageableDefault(size = 10, sort = "dateCandidature", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return candidatureService.listerCandidaturesOffre(JwtClaims.utilisateurId(jwt), offreId, pageable);
    }

    @PatchMapping("/candidatures/{id}/statut")
    @Operation(summary = "Changer le statut d'une candidature (EN_ATTENTE → ENTRETIEN|REFUSEE, ENTRETIEN → ACCEPTEE|REFUSEE)")
    public CandidatureEntrepriseResponse changerStatut(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                       @Valid @RequestBody ChangementStatutCandidatureRequest request) {
        return candidatureService.changerStatut(JwtClaims.utilisateurId(jwt), id, request.statut());
    }
}
