package com.stagehub.notification.service;

import com.stagehub.notification.event.CandidatureStatutChange;
import com.stagehub.notification.event.EntrepriseValidation;
import com.stagehub.notification.repository.EvenementTraiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-23T10:00:00Z");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private EvenementTraiteRepository evenementTraiteRepository;
    @Mock
    private EmailService emailService;

    private NotificationService service;

    @BeforeEach
    void creer() {
        service = new NotificationService(evenementTraiteRepository, emailService, "http://front",
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    void nouveauChangementDeStatut_emailAvecLeModeleDuNouveauStatut() {
        when(evenementTraiteRepository.inscrire(ID, "CandidatureStatutChange", MAINTENANT)).thenReturn(1);

        service.surChangementStatutCandidature(ID, new CandidatureStatutChange(5L, "sara@etu.ma", "Sara",
                "Stage Java", "Acme", "EN_ATTENTE", "ENTRETIEN"));

        verify(emailService).envoyer("sara@etu.ma", "candidature-entretien", Map.of(
                "prenom", "Sara", "offreTitre", "Stage Java", "entrepriseNom", "Acme",
                "lienCandidatures", "http://front/etudiant/candidatures", "lienOffres", "http://front/offres"));
    }

    @Test
    void evenementDejaTraite_aucunEmail() {
        when(evenementTraiteRepository.inscrire(ID, "CandidatureStatutChange", MAINTENANT)).thenReturn(0);

        service.surChangementStatutCandidature(ID, new CandidatureStatutChange(5L, "sara@etu.ma", "Sara",
                "Stage Java", "Acme", "ENTRETIEN", "ACCEPTEE"));

        verifyNoInteractions(emailService);
    }

    @Test
    void validationEntreprise_emailAvecLeModeleDeLaDecision() {
        when(evenementTraiteRepository.inscrire(ID, "EntrepriseValidation", MAINTENANT)).thenReturn(1);

        service.surValidationEntreprise(ID, new EntrepriseValidation(2L, "rh@acme.ma", "Acme", "REFUSEE"));

        verify(emailService).envoyer(eq("rh@acme.ma"), eq("entreprise-refusee"),
                eq(Map.of("nom", "Acme", "lienEspace", "http://front/entreprise/offres")));
    }

    @Test
    void validationEntrepriseDejaTraitee_aucunEmail() {
        when(evenementTraiteRepository.inscrire(any(), anyString(), any())).thenReturn(0);

        service.surValidationEntreprise(ID, new EntrepriseValidation(2L, "rh@acme.ma", "Acme", "VALIDEE"));

        verifyNoInteractions(emailService);
    }
}
