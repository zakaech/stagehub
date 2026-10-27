package com.stagehub.cv.controller;

import com.stagehub.cv.service.CvService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * API interne (phase 8 c) : CV figé d'une candidature, lu par ai-service pour l'analyse. Réservée au sujet
 * « ai-service » (SecurityConfig), jamais exposée par la gateway, absente du Swagger public. Le PDF seul, sans nom
 * de fichier : ai-service n'a pas besoin du nom de l'étudiant.
 */
@RestController
@Hidden
public class CvInterneController {

    private final CvService cvService;

    public CvInterneController(CvService cvService) {
        this.cvService = cvService;
    }

    @GetMapping(path = "/internal/candidatures/{id}/cv", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> cv(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .body(cvService.cvPourAnalyse(id).contenu());
    }
}
