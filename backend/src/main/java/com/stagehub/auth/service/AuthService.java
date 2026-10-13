package com.stagehub.auth.service;

import com.stagehub.auth.dto.LoginRequest;
import com.stagehub.auth.dto.ProfilResponse;
import com.stagehub.auth.dto.RegisterEntrepriseRequest;
import com.stagehub.auth.dto.RegisterEtudiantRequest;
import com.stagehub.auth.dto.TokenResponse;
import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import com.stagehub.common.exception.EmailDejaUtiliseException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.mapper.EntrepriseMapper;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.mapper.EtudiantMapper;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final EtudiantMapper etudiantMapper;
    private final EntrepriseMapper entrepriseMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UtilisateurRepository utilisateurRepository, EtudiantRepository etudiantRepository,
                       EntrepriseRepository entrepriseRepository, EtudiantMapper etudiantMapper,
                       EntrepriseMapper entrepriseMapper, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.utilisateurRepository = utilisateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.etudiantMapper = etudiantMapper;
        this.entrepriseMapper = entrepriseMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public ProfilResponse inscrireEtudiant(RegisterEtudiantRequest request) {
        Utilisateur utilisateur = creerUtilisateur(request.email(), request.motDePasse(), Role.ETUDIANT);

        Etudiant etudiant = new Etudiant();
        etudiant.setUtilisateur(utilisateur);
        etudiant.setNom(request.nom());
        etudiant.setPrenom(request.prenom());
        etudiant.setEcole(request.ecole());
        etudiant.setFiliere(request.filiere());
        etudiant.setVille(request.ville());
        etudiantRepository.save(etudiant);

        return profil(utilisateur, etudiant, null);
    }

    /** Le compte est créé EN_ATTENTE : l'entreprise peut se connecter mais pas publier avant validation. */
    @Transactional
    public ProfilResponse inscrireEntreprise(RegisterEntrepriseRequest request) {
        Utilisateur utilisateur = creerUtilisateur(request.email(), request.motDePasse(), Role.ENTREPRISE);

        Entreprise entreprise = new Entreprise();
        entreprise.setUtilisateur(utilisateur);
        entreprise.setNom(request.nom());
        entreprise.setSecteur(request.secteur());
        entreprise.setVille(request.ville());
        entreprise.setDescription(request.description());
        entreprise.setStatutValidation(StatutValidation.EN_ATTENTE);
        entrepriseRepository.save(entreprise);

        return profil(utilisateur, null, entreprise);
    }

    @Transactional(readOnly = true)
    public TokenResponse connecter(LoginRequest request) {
        String email = Utilisateur.normaliserEmail(request.email());
        // Lève BadCredentialsException (→ 401) si l'email est inconnu ou le mot de passe faux
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.motDePasse()));

        Utilisateur utilisateur = utilisateurRepository.findByEmail(email).orElseThrow();
        return jwtService.genererToken(utilisateur);
    }

    @Transactional(readOnly = true)
    public ProfilResponse profil(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new UtilisateurNotFoundException(utilisateurId));
        return switch (utilisateur.getRole()) {
            case ETUDIANT -> profil(utilisateur, etudiantRepository.findById(utilisateurId).orElseThrow(), null);
            case ENTREPRISE -> profil(utilisateur, null, entrepriseRepository.findById(utilisateurId).orElseThrow());
            case ADMIN -> profil(utilisateur, null, null);
        };
    }

    private Utilisateur creerUtilisateur(String emailBrut, String motDePasse, Role role) {
        String email = Utilisateur.normaliserEmail(emailBrut);
        if (utilisateurRepository.existsByEmail(email)) {
            throw new EmailDejaUtiliseException(email);
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        utilisateur.setRole(role);
        return utilisateurRepository.save(utilisateur);
    }

    private ProfilResponse profil(Utilisateur utilisateur, Etudiant etudiant, Entreprise entreprise) {
        return new ProfilResponse(
                utilisateur.getId(),
                utilisateur.getEmail(),
                utilisateur.getRole(),
                utilisateur.getDateCreation(),
                etudiant == null ? null : etudiantMapper.toResponse(etudiant),
                entreprise == null ? null : entrepriseMapper.toResponse(entreprise));
    }
}
