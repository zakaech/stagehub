package com.stagehub.analyse.controller;

import com.stagehub.analyse.dto.FournisseurIa;
import com.stagehub.analyse.dto.InformationAnalyseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Information de l'étudiant sur l'analyse de son CV (spec §1) : le dialogue de candidature affiche le texte du
 * fournisseur configuré. Une valeur inconnue de {@code IA_FOURNISSEUR} empêche le démarrage (comme dans ai-service).
 */
@RestController
@RequestMapping("/api/v1/etudiant")
@Tag(name = "Espace étudiant — candidatures")
public class InformationAnalyseController {

    private final InformationAnalyseResponse information;

    public InformationAnalyseController(@Value("${stagehub.analyse.fournisseur:ollama}") String fournisseur) {
        this.information = new InformationAnalyseResponse(FournisseurIa.depuis(fournisseur));
    }

    @GetMapping("/analyse-ia")
    @Operation(summary = "Fournisseur qui analysera le CV (le texte d'information en dépend)")
    public InformationAnalyseResponse information() {
        return information;
    }
}
