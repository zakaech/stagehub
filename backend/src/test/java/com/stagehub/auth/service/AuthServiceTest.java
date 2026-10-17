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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private EntrepriseRepository entrepriseRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(utilisateurRepository, etudiantRepository, entrepriseRepository,
                Mappers.getMapper(EtudiantMapper.class), Mappers.getMapper(EntrepriseMapper.class),
                passwordEncoder, authenticationManager, jwtService);
    }

    @Test
    void inscrireEtudiant_normaliseLEmailEtHacheLeMotDePasse() {
        when(passwordEncoder.encode("motdepasse1")).thenReturn("hash-bcrypt");
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfilResponse profil = service.inscrireEtudiant(new RegisterEtudiantRequest(
                "  Sara@Etu.MA ", "motdepasse1", "Alaoui", "Sara", "ENSIAS", "GL", "Rabat"));

        ArgumentCaptor<Utilisateur> utilisateur = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepository).save(utilisateur.capture());
        assertThat(utilisateur.getValue().getEmail()).isEqualTo("sara@etu.ma");
        assertThat(utilisateur.getValue().getMotDePasse()).isEqualTo("hash-bcrypt");
        assertThat(utilisateur.getValue().getRole()).isEqualTo(Role.ETUDIANT);
        verify(etudiantRepository).save(any(Etudiant.class));
        assertThat(profil.etudiant().prenom()).isEqualTo("Sara");
        assertThat(profil.entreprise()).isNull();
    }

    @Test
    void inscription_emailDejaUtilise_leve409SansRienEnregistrer() {
        when(utilisateurRepository.existsByEmail("sara@etu.ma")).thenReturn(true);

        assertThatThrownBy(() -> service.inscrireEtudiant(new RegisterEtudiantRequest(
                "sara@etu.ma", "motdepasse1", "Alaoui", "Sara", "ENSIAS", "GL", "Rabat")))
                .isInstanceOf(EmailDejaUtiliseException.class);
        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    void inscrireEntreprise_creeUnCompteEnAttente() {
        when(utilisateurRepository.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfilResponse profil = service.inscrireEntreprise(new RegisterEntrepriseRequest(
                "rh@acme.ma", "motdepasse1", "Acme", "IT", "Casablanca", null));

        assertThat(profil.role()).isEqualTo(Role.ENTREPRISE);
        assertThat(profil.entreprise().statutValidation()).isEqualTo(StatutValidation.EN_ATTENTE);
    }

    @Test
    void connecter_verifieLesIdentifiantsPuisGenereLeJeton() {
        Utilisateur sara = utilisateur(1L, "sara@etu.ma", Role.ETUDIANT);
        TokenResponse jeton = new TokenResponse("jwt", "Bearer", 3600, Role.ETUDIANT);
        when(utilisateurRepository.findByEmail("sara@etu.ma")).thenReturn(Optional.of(sara));
        when(jwtService.genererToken(sara)).thenReturn(jeton);

        assertThat(service.connecter(new LoginRequest(" SARA@etu.ma", "motdepasse1"))).isEqualTo(jeton);
        verify(authenticationManager).authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("sara@etu.ma", "motdepasse1"));
    }

    @Test
    void connecter_mauvaisMotDePasse_laisseRemonterBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("refusé"));

        assertThatThrownBy(() -> service.connecter(new LoginRequest("sara@etu.ma", "faux")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).genererToken(any());
    }

    @Test
    void profil_selonLeRole() {
        Etudiant etudiant = new Etudiant();
        etudiant.setPrenom("Sara");
        Entreprise entreprise = new Entreprise();
        entreprise.setNom("Acme");
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(utilisateur(1L, "sara@etu.ma", Role.ETUDIANT)));
        when(utilisateurRepository.findById(2L)).thenReturn(Optional.of(utilisateur(2L, "rh@acme.ma", Role.ENTREPRISE)));
        when(utilisateurRepository.findById(3L)).thenReturn(Optional.of(utilisateur(3L, "admin@x.ma", Role.ADMIN)));
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.of(entreprise));

        assertThat(service.profil(1L).etudiant().prenom()).isEqualTo("Sara");
        assertThat(service.profil(2L).entreprise().nom()).isEqualTo("Acme");
        ProfilResponse admin = service.profil(3L);
        assertThat(admin.etudiant()).isNull();
        assertThat(admin.entreprise()).isNull();
    }

    @Test
    void profil_utilisateurInexistant_leve404() {
        when(utilisateurRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.profil(9L)).isInstanceOf(UtilisateurNotFoundException.class);
    }

    private static Utilisateur utilisateur(Long id, String email, Role role) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setEmail(email);
        utilisateur.setRole(role);
        return utilisateur;
    }
}
