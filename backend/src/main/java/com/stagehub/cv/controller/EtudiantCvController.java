package com.stagehub.cv.controller;

import com.stagehub.cv.service.CvService;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/etudiant/cv")
@Tag(name = "Espace étudiant — CV")
public class EtudiantCvController {

    private final CvService cvService;

    public EtudiantCvController(CvService cvService) {
        this.cvService = cvService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Déposer ou remplacer mon CV (PDF, 5 Mo maximum)")
    public EtudiantResponse deposer(@AuthenticationPrincipal Jwt jwt, @RequestParam("fichier") MultipartFile fichier) {
        return cvService.deposer(JwtClaims.utilisateurId(jwt), fichier);
    }
}
