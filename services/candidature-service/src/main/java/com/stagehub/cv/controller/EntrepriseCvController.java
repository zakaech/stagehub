package com.stagehub.cv.controller;

import com.stagehub.cv.service.CvService;
import com.stagehub.commons.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Espace entreprise — candidatures")
public class EntrepriseCvController {

    private final CvService cvService;

    public EntrepriseCvController(CvService cvService) {
        this.cvService = cvService;
    }

    @GetMapping(path = "/api/v1/entreprise/candidatures/{id}/cv", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Télécharger le CV joint à une candidature reçue (celui en vigueur quand l'étudiant a postulé)")
    public ResponseEntity<Resource> telecharger(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ReponsesPdf.telechargement(cvService.telechargerCvCandidature(JwtClaims.utilisateurId(jwt), id));
    }
}
