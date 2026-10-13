package com.stagehub.offre.service;

import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.EntrepriseNonValideeException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.dto.OffreRequest;
import com.stagehub.offre.dto.OffreResponse;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.mapper.OffreMapper;
import com.stagehub.offre.repository.OffreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffreServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 1);
    private static final Long ENTREPRISE_ID = 2L;
    private static final Long AUTRE_ENTREPRISE_ID = 3L;
    private static final Long OFFRE_ID = 10L;

    @Mock
    private OffreRepository offreRepository;
    @Mock
    private EntrepriseRepository entrepriseRepository;

    private OffreService service;
    private Entreprise entreprise;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AUJOURDHUI.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new OffreService(offreRepository, entrepriseRepository, Mappers.getMapper(OffreMapper.class), clock);

        entreprise = new Entreprise();
        entreprise.setId(ENTREPRISE_ID);
        entreprise.setNom("Acme");
        entreprise.setStatutValidation(StatutValidation.VALIDEE);
    }

    @Nested
    class ChangerStatut {

        @Test
        void fermerUneOffreOuverte_laFerme() {
            Offre offre = offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            OffreResponse reponse = service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.FERMEE);

            assertThat(reponse.statut()).isEqualTo(StatutOffre.FERMEE);
        }

        @Test
        void fermerUneOffreDejaFermee_estIdempotent() {
            Offre offre = offre(StatutOffre.FERMEE, AUJOURDHUI.minusDays(5));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            assertThat(service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.FERMEE).statut())
                    .isEqualTo(StatutOffre.FERMEE);
        }

        @Test
        void rouvrirAvecDateLimiteFuture_laRouvre() {
            Offre offre = offre(StatutOffre.FERMEE, AUJOURDHUI.plusDays(10));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            assertThat(service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.OUVERTE).statut())
                    .isEqualTo(StatutOffre.OUVERTE);
        }

        @Test
        void rouvrirAvecDateLimiteAujourdhui_laRouvre() {
            Offre offre = offre(StatutOffre.FERMEE, AUJOURDHUI);
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            assertThat(service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.OUVERTE).statut())
                    .isEqualTo(StatutOffre.OUVERTE);
        }

        @Test
        void rouvrirAvecDateLimiteDepassee_leveTransitionStatutInvalideSansModifier() {
            Offre offre = offre(StatutOffre.FERMEE, AUJOURDHUI.minusDays(1));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.OUVERTE))
                    .isInstanceOf(TransitionStatutInvalideException.class)
                    .hasMessageContaining("date limite");
            assertThat(offre.getStatut()).isEqualTo(StatutOffre.FERMEE);
        }

        @Test
        void offreDUneAutreEntreprise_leveAccesNonAutorise() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));

            assertThatThrownBy(() -> service.changerStatut(AUTRE_ENTREPRISE_ID, OFFRE_ID, StatutOffre.FERMEE))
                    .isInstanceOf(AccesNonAutoriseException.class);
        }

        @Test
        void offreInexistante_leveOffreNotFound() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.FERMEE))
                    .isInstanceOf(OffreNotFoundException.class);
        }
    }

    @Nested
    class Creer {

        private final OffreRequest request = new OffreRequest("Stage PFE Java", "API Spring Boot", "Casablanca",
                "Développement", Set.of("Java"), 6, AUJOURDHUI.plusDays(30));

        @Test
        void entrepriseValidee_publieUneOffreOuverte() {
            when(entrepriseRepository.findById(ENTREPRISE_ID)).thenReturn(Optional.of(entreprise));
            when(offreRepository.save(any(Offre.class))).thenAnswer(invocation -> invocation.getArgument(0));

            OffreResponse reponse = service.creer(ENTREPRISE_ID, request);

            assertThat(reponse.statut()).isEqualTo(StatutOffre.OUVERTE);
            assertThat(reponse.entrepriseNom()).isEqualTo("Acme");
            assertThat(reponse.technologies()).containsExactly("Java");
        }

        @Test
        void entrepriseEnAttente_leveEntrepriseNonValidee() {
            entreprise.setStatutValidation(StatutValidation.EN_ATTENTE);
            when(entrepriseRepository.findById(ENTREPRISE_ID)).thenReturn(Optional.of(entreprise));

            assertThatThrownBy(() -> service.creer(ENTREPRISE_ID, request))
                    .isInstanceOf(EntrepriseNonValideeException.class)
                    .hasMessageContaining("en attente");
            verify(offreRepository, never()).save(any());
        }
    }

    private Offre offre(StatutOffre statut, LocalDate dateLimite) {
        Offre offre = new Offre();
        offre.setId(OFFRE_ID);
        offre.setTitre("Stage PFE Java");
        offre.setEntreprise(entreprise);
        offre.setStatut(statut);
        offre.setDateLimite(dateLimite);
        return offre;
    }
}
