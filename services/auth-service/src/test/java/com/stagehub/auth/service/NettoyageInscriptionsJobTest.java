package com.stagehub.auth.service;

import com.stagehub.auth.client.ProfilEntrepriseClient;
import com.stagehub.auth.client.ProfilEtudiantClient;
import com.stagehub.auth.entity.Role;
import com.stagehub.auth.entity.StatutCompte;
import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.auth.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettoyageInscriptionsJobTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-23T10:00:00Z");

    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private CompteService compteService;
    @Mock
    private ProfilEtudiantClient profilEtudiantClient;
    @Mock
    private ProfilEntrepriseClient profilEntrepriseClient;

    private NettoyageInscriptionsJob job;

    @BeforeEach
    void creer() {
        job = new NettoyageInscriptionsJob(utilisateurRepository, compteService, profilEtudiantClient,
                profilEntrepriseClient, Duration.ofMinutes(15), Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    void chercheLesComptesEnAttenteDepuisPlusDuDelai() {
        job.nettoyer();

        verify(utilisateurRepository).findByStatutAndDateCreationBefore(
                eq(StatutCompte.EN_ATTENTE), eq(MAINTENANT.minus(Duration.ofMinutes(15))));
    }

    @Test
    void supprimeLeProfilDansLeBonServicePuisLeCompte() {
        when(utilisateurRepository.findByStatutAndDateCreationBefore(eq(StatutCompte.EN_ATTENTE), eq(MAINTENANT.minus(Duration.ofMinutes(15)))))
                .thenReturn(List.of(compte(1L, Role.ETUDIANT), compte(2L, Role.ENTREPRISE)));

        job.nettoyer();

        verify(profilEtudiantClient).supprimer(1L);
        verify(profilEntrepriseClient).supprimer(2L);
        verify(compteService).supprimer(1L);
        verify(compteService).supprimer(2L);
    }

    @Test
    void adminEnAttente_aucunProfilATraiter() {
        when(utilisateurRepository.findByStatutAndDateCreationBefore(eq(StatutCompte.EN_ATTENTE), eq(MAINTENANT.minus(Duration.ofMinutes(15)))))
                .thenReturn(List.of(compte(3L, Role.ADMIN)));

        job.nettoyer();

        verifyNoInteractions(profilEtudiantClient, profilEntrepriseClient);
        verify(compteService).supprimer(3L);
    }

    @Test
    void serviceDesProfilsEnPanne_compteGardePourLeProchainPassage_lesAutresContinuent() {
        when(utilisateurRepository.findByStatutAndDateCreationBefore(eq(StatutCompte.EN_ATTENTE), eq(MAINTENANT.minus(Duration.ofMinutes(15)))))
                .thenReturn(List.of(compte(4L, Role.ETUDIANT), compte(5L, Role.ENTREPRISE)));
        doThrow(new IllegalStateException("injoignable")).when(profilEtudiantClient).supprimer(4L);

        job.nettoyer();

        verify(compteService, never()).supprimer(4L);
        verify(compteService).supprimer(5L);
    }

    private static Utilisateur compte(Long id, Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setEmail("compte-" + id + "@test.local");
        u.setRole(role);
        u.setStatut(StatutCompte.EN_ATTENTE);
        return u;
    }
}
