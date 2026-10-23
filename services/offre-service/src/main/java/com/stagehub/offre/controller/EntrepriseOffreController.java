package com.stagehub.offre.controller;

import com.stagehub.commons.dto.PageResponse;
import com.stagehub.offre.dto.ChangementStatutOffreRequest;
import com.stagehub.offre.dto.OffreRequest;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.service.OffreService;
import com.stagehub.commons.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Gestion de ses propres offres par une entreprise (rôle ENTREPRISE, cf. SecurityConfig). */
@RestController
@RequestMapping("/api/v1/entreprise/offres")
@Tag(name = "Espace entreprise — offres")
public class EntrepriseOffreController {

    private final OffreService offreService;

    public EntrepriseOffreController(OffreService offreService) {
        this.offreService = offreService;
    }

    @GetMapping
    @Operation(summary = "Mes offres (tous statuts)")
    public PageResponse<OffreResponse> mesOffres(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return offreService.listerOffresEntreprise(JwtClaims.utilisateurId(jwt), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une de mes offres")
    public OffreResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return offreService.getOffreEntreprise(JwtClaims.utilisateurId(jwt), id);
    }

    @PostMapping
    @Operation(summary = "Publier une offre (entreprise validée uniquement)")
    public ResponseEntity<OffreResponse> creer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OffreRequest request) {
        OffreResponse offre = offreService.creer(JwtClaims.utilisateurId(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(offre.id()).toUri();
        return ResponseEntity.created(location).body(offre);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une de mes offres")
    public OffreResponse modifier(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                  @Valid @RequestBody OffreRequest request) {
        return offreService.modifier(JwtClaims.utilisateurId(jwt), id, request);
    }

    @PatchMapping("/{id}/statut")
    @Operation(summary = "Fermer une de mes offres, ou la rouvrir si sa date limite n'est pas dépassée")
    public OffreResponse changerStatut(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                       @Valid @RequestBody ChangementStatutOffreRequest request) {
        return offreService.changerStatut(JwtClaims.utilisateurId(jwt), id, request.statut());
    }
}
