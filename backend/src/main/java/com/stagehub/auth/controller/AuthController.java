package com.stagehub.auth.controller;

import com.stagehub.auth.dto.LoginRequest;
import com.stagehub.auth.dto.ProfilResponse;
import com.stagehub.auth.dto.RegisterEntrepriseRequest;
import com.stagehub.auth.dto.RegisterEtudiantRequest;
import com.stagehub.auth.dto.TokenResponse;
import com.stagehub.auth.service.AuthService;
import com.stagehub.security.JwtClaims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/etudiant")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Inscription d'un étudiant")
    public ProfilResponse inscrireEtudiant(@Valid @RequestBody RegisterEtudiantRequest request) {
        return authService.inscrireEtudiant(request);
    }

    @PostMapping("/register/entreprise")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Inscription d'une entreprise (compte en attente de validation)")
    public ProfilResponse inscrireEntreprise(@Valid @RequestBody RegisterEntrepriseRequest request) {
        return authService.inscrireEntreprise(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Connexion : renvoie un access token JWT valable 1 h")
    public TokenResponse connecter(@Valid @RequestBody LoginRequest request) {
        return authService.connecter(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Profil de l'utilisateur connecté")
    public ProfilResponse moi(@AuthenticationPrincipal Jwt jwt) {
        return authService.profil(JwtClaims.utilisateurId(jwt));
    }
}
