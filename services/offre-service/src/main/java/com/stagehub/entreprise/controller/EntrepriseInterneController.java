package com.stagehub.entreprise.controller;

import com.stagehub.entreprise.dto.CreationEntrepriseRequest;
import com.stagehub.entreprise.dto.EntrepriseResponse;
import com.stagehub.entreprise.service.EntrepriseProfilService;
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

/** API interne (entre services), réservée aux jetons de rôle SERVICE ; jamais exposée par la gateway. */
@RestController
@RequestMapping("/internal/entreprises")
@Hidden
public class EntrepriseInterneController {

    private final EntrepriseProfilService entrepriseProfilService;

    public EntrepriseInterneController(EntrepriseProfilService entrepriseProfilService) {
        this.entrepriseProfilService = entrepriseProfilService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntrepriseResponse creer(@Valid @RequestBody CreationEntrepriseRequest demande) {
        return entrepriseProfilService.creer(demande);
    }

    @GetMapping("/{id}")
    public EntrepriseResponse lire(@PathVariable Long id) {
        return entrepriseProfilService.lire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        entrepriseProfilService.supprimer(id);
    }
}
