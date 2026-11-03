package com.stagehub.ai.service;

import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.event.CandidatureCreee;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import com.stagehub.ai.repository.EvenementTraiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandeAnalyseServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-24T10:00:00Z");
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final UUID EVENEMENT = UUID.randomUUID();

    @Mock
    private EvenementTraiteRepository evenementTraiteRepository;
    @Mock
    private DemandeAnalyseRepository demandeAnalyseRepository;

    private DemandeAnalyseService service;

    @BeforeEach
    void setUp() {
        service = new DemandeAnalyseService(evenementTraiteRepository, demandeAnalyseRepository,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    void nouvelEvenement_demandeEnregistreeEnAttente() {
        when(evenementTraiteRepository.inscrire(EVENEMENT, "CandidatureCreee", MAINTENANT)).thenReturn(1);

        service.surCandidatureCreee(EVENEMENT, new CandidatureCreee(8L, 3L, 5L, "cv.pdf"), TRACEPARENT);

        ArgumentCaptor<DemandeAnalyse> demande = ArgumentCaptor.forClass(DemandeAnalyse.class);
        verify(demandeAnalyseRepository).save(demande.capture());
        assertThat(demande.getValue().getCandidatureId()).isEqualTo(8L);
        assertThat(demande.getValue().getOffreId()).isEqualTo(3L);
        assertThat(demande.getValue().getEtudiantId()).isEqualTo(5L);
        assertThat(demande.getValue().getCvFichier()).isEqualTo("cv.pdf");
        assertThat(demande.getValue().getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        assertThat(demande.getValue().getDateReception()).isEqualTo(MAINTENANT);
        // Contexte de trace reçu avec l'événement, repris par l'analyse
        assertThat(demande.getValue().getTraceparent()).isEqualTo(TRACEPARENT);
    }

    @Test
    void candidatureSansCv_demandeEnregistreeQuandMeme() {
        when(evenementTraiteRepository.inscrire(EVENEMENT, "CandidatureCreee", MAINTENANT)).thenReturn(1);

        service.surCandidatureCreee(EVENEMENT, new CandidatureCreee(9L, 3L, 5L, null), null);

        ArgumentCaptor<DemandeAnalyse> demande = ArgumentCaptor.forClass(DemandeAnalyse.class);
        verify(demandeAnalyseRepository).save(demande.capture());
        assertThat(demande.getValue().getCvFichier()).isNull();
    }

    @Test
    void evenementDejaTraite_rienNEstEnregistre() {
        when(evenementTraiteRepository.inscrire(EVENEMENT, "CandidatureCreee", MAINTENANT)).thenReturn(0);

        service.surCandidatureCreee(EVENEMENT, new CandidatureCreee(8L, 3L, 5L, "cv.pdf"), TRACEPARENT);

        verifyNoInteractions(demandeAnalyseRepository);
    }
}
