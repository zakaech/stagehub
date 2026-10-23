package com.stagehub.offre.controller;

import com.stagehub.offre.dto.OffreInterneResponse;
import com.stagehub.offre.dto.OffreResumeResponse;
import com.stagehub.offre.service.OffreService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API interne (entre services) : lue par le service des candidatures, avec le jeton de l'utilisateur relayé.
 * Jamais exposée par la gateway ; absente du Swagger public. Toutes les offres, quel que soit leur statut (un
 * étudiant suit sa candidature même sur une offre fermée).
 */
@RestController
@RequestMapping("/internal/offres")
@Validated
@Hidden
public class OffreInterneController {

    private final OffreService offreService;

    public OffreInterneController(OffreService offreService) {
        this.offreService = offreService;
    }

    @GetMapping("/{id}")
    public OffreInterneResponse offre(@PathVariable Long id) {
        return offreService.getOffreInterne(id);
    }

    /** Résumés de plusieurs offres en un seul appel (une page de candidatures = un appel, pas un par ligne). */
    @GetMapping
    public List<OffreResumeResponse> resumes(@RequestParam @Size(max = 100) List<Long> ids) {
        return offreService.resumes(ids);
    }
}
