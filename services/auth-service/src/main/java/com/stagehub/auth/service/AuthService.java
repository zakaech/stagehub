package com.stagehub.auth.service;

import com.stagehub.auth.client.ProfilEntrepriseClient;
import com.stagehub.auth.client.ProfilEtudiantClient;
import com.stagehub.auth.dto.LoginRequest;
import com.stagehub.auth.dto.ProfilResponse;
import com.stagehub.auth.dto.TokenResponse;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.exception.ServiceIndisponibleException;
import com.stagehub.auth.repository.UtilisateurRepository;
import com.stagehub.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteService compteService;
    private final JwtService jwtService;
    private final ProfilEtudiantClient profilEtudiantClient;
    private final ProfilEntrepriseClient profilEntrepriseClient;

    public AuthService(AuthenticationManager authenticationManager, UtilisateurRepository utilisateurRepository,
                       CompteService compteService, JwtService jwtService, ProfilEtudiantClient profilEtudiantClient,
                       ProfilEntrepriseClient profilEntrepriseClient) {
        this.authenticationManager = authenticationManager;
        this.utilisateurRepository = utilisateurRepository;
        this.compteService = compteService;
        this.jwtService = jwtService;
        this.profilEtudiantClient = profilEtudiantClient;
        this.profilEntrepriseClient = profilEntrepriseClient;
    }

    public TokenResponse connecter(LoginRequest request) {
        String email = Utilisateur.normaliserEmail(request.email());
        // BadCredentialsException (email inconnu, mot de passe faux) ou DisabledException (compte en attente) → 401
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.motDePasse()));
        Utilisateur utilisateur = utilisateurRepository.findByEmail(email).orElseThrow();
        return jwtService.genererToken(utilisateur);
    }

    /**
     * Compte (ici) + profil (lu dans le service qui le détient, avec un jeton de service) : même réponse que le
     * monolithe. Si le service des profils ne répond pas, 503 plutôt qu'un profil incomplet.
     */
    public ProfilResponse profil(Long utilisateurId) {
        Utilisateur u = compteService.trouver(utilisateurId);
        try {
            return switch (u.getRole()) {
                case ETUDIANT -> new ProfilResponse(u.getId(), u.getEmail(), u.getRole(), u.getDateCreation(),
                        profilEtudiantClient.lire(u.getId()), null);
                case ENTREPRISE -> new ProfilResponse(u.getId(), u.getEmail(), u.getRole(), u.getDateCreation(),
                        null, profilEntrepriseClient.lire(u.getId()));
                case ADMIN -> new ProfilResponse(u.getId(), u.getEmail(), u.getRole(), u.getDateCreation(), null, null);
            };
        } catch (RuntimeException e) {
            log.warn("Profil {} illisible : {}", utilisateurId, e.getMessage());
            throw new ServiceIndisponibleException("Profil momentanément indisponible, réessayez dans un instant");
        }
    }
}
