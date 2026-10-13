package com.stagehub.candidature.controller;

import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.dto.CandidatureRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/etudiant")
@Tag(name = "Espace étudiant — candidatures")
public class EtudiantCandidatureController {

    private final CandidatureService candidatureService;

    public EtudiantCandidatureController(CandidatureService candidatureService) {
        this.candidatureService = candidatureService;
    }

    @PostMapping("/offres/{offreId}/candidatures")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Postuler à une offre (lettre de motivation optionnelle)")
    public CandidatureEtudiantResponse postuler(@AuthenticationPrincipal Jwt jwt, @PathVariable Long offreId,
                                                @Valid @RequestBody(required = false) CandidatureRequest request) {
        return candidatureService.postuler(JwtClaims.utilisateurId(jwt), offreId, request);
    }

    @GetMapping("/candidatures")
    @Operation(summary = "Mes candidatures et leur statut")
    public PageResponse<CandidatureEtudiantResponse> mesCandidatures(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 10, sort = "dateCandidature", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return candidatureService.listerCandidaturesEtudiant(JwtClaims.utilisateurId(jwt), pageable);
    }
}
