package com.stagehub.etudiant.controller;

import com.stagehub.etudiant.dto.CreationEtudiantRequest;
import com.stagehub.etudiant.dto.EtudiantResponse;
import com.stagehub.etudiant.service.EtudiantProfilService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API interne (entre services), réservée aux jetons de rôle SERVICE (SecurityConfig) et jamais exposée par la gateway.
 * Absente du Swagger public.
 */
@RestController
@RequestMapping("/internal/etudiants")
@Hidden
public class EtudiantInterneController {

    private final EtudiantProfilService etudiantProfilService;

    public EtudiantInterneController(EtudiantProfilService etudiantProfilService) {
        this.etudiantProfilService = etudiantProfilService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EtudiantResponse creer(@Valid @RequestBody CreationEtudiantRequest demande) {
        return etudiantProfilService.creer(demande);
    }

    @GetMapping("/{id}")
    public EtudiantResponse lire(@PathVariable Long id) {
        return etudiantProfilService.lire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        etudiantProfilService.supprimer(id);
    }
}
