package com.stagehub.offre.service;

import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.EntrepriseNonValideeException;
import com.stagehub.common.exception.EntrepriseNotFoundException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.entreprise.entity.StatutValidation;
import com.stagehub.entreprise.repository.EntrepriseRepository;
import com.stagehub.offre.dto.OffreFiltre;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    void fermerOffresExpirees_fermeCellesAvantAujourdhuiDansLeFuseauDeReference() {
        when(offreRepository.fermerOffresExpirees(AUJOURDHUI, StatutOffre.OUVERTE, StatutOffre.FERMEE)).thenReturn(3);

        assertThat(service.fermerOffresExpirees()).isEqualTo(3);
        verify(offreRepository).fermerOffresExpirees(AUJOURDHUI, StatutOffre.OUVERTE, StatutOffre.FERMEE);
    }

    @Nested
    class Consultation {

        private final PageRequest page = PageRequest.of(0, 10);

        @Test
        void rechercherOuvertes_passeLaSpecificationEtLaPagination() {
            Offre offre = offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10));
            when(offreRepository.findAll(any(Specification.class), eq(page)))
                    .thenReturn(new PageImpl<>(List.of(offre), page, 1));

            var resultat = service.rechercherOffresOuvertes(new OffreFiltre("Rabat", null, List.of("Java")), page);

            assertThat(resultat.content()).extracting(OffreResponse::id).containsExactly(OFFRE_ID);
            assertThat(resultat.totalElements()).isEqualTo(1);
        }

        @Test
        void filtresDisponibles_regroupeLesTroisListes() {
            when(offreRepository.villesDistinctes(StatutOffre.OUVERTE)).thenReturn(List.of("Rabat"));
            when(offreRepository.domainesDistincts(StatutOffre.OUVERTE)).thenReturn(List.of("Data"));
            when(offreRepository.technologiesDistinctes(StatutOffre.OUVERTE)).thenReturn(List.of("Java", "SQL"));

            var filtres = service.filtresDisponibles();

            assertThat(filtres.villes()).containsExactly("Rabat");
            assertThat(filtres.domaines()).containsExactly("Data");
            assertThat(filtres.technologies()).containsExactly("Java", "SQL");
        }

        @Test
        void detailPublic_offreOuverte_ou404() {
            when(offreRepository.findByIdAndStatut(OFFRE_ID, StatutOffre.OUVERTE))
                    .thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));
            when(offreRepository.findByIdAndStatut(99L, StatutOffre.OUVERTE)).thenReturn(Optional.empty());

            assertThat(service.getOffreOuverte(OFFRE_ID).entrepriseNom()).isEqualTo("Acme");
            assertThatThrownBy(() -> service.getOffreOuverte(99L)).isInstanceOf(OffreNotFoundException.class);
        }

        @Test
        void espaceEntreprise_listeEtDetailDeSesOffres() {
            Offre offre = offre(StatutOffre.FERMEE, AUJOURDHUI.minusDays(3));
            when(offreRepository.findByEntrepriseId(ENTREPRISE_ID, page)).thenReturn(new PageImpl<>(List.of(offre), page, 1));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            assertThat(service.listerOffresEntreprise(ENTREPRISE_ID, page).content()).hasSize(1);
            assertThat(service.getOffreEntreprise(ENTREPRISE_ID, OFFRE_ID).statut()).isEqualTo(StatutOffre.FERMEE);
        }
    }

    @Nested
    class Modifier {

        private final OffreRequest modification = new OffreRequest("Nouveau titre", "Nouvelle description",
                " Fès ", "Data", new LinkedHashSet<>(List.of("Python")), 4, AUJOURDHUI.plusDays(20));

        @Test
        void entrepriseValideeProprietaire_metAJourEnPlace() {
            Offre offre = offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10));
            offre.getTechnologies().add("Java");
            when(entrepriseRepository.findById(ENTREPRISE_ID)).thenReturn(Optional.of(entreprise));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));

            OffreResponse reponse = service.modifier(ENTREPRISE_ID, OFFRE_ID, modification);

            assertThat(reponse.titre()).isEqualTo("Nouveau titre");
            assertThat(reponse.ville()).isEqualTo("Fès");
            assertThat(reponse.technologies()).containsExactly("Python");
            assertThat(offre.getTitre()).as("l'entité gérée est modifiée, sans save()").isEqualTo("Nouveau titre");
        }

        @Test
        void entrepriseInexistante_leve404() {
            when(entrepriseRepository.findById(ENTREPRISE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.modifier(ENTREPRISE_ID, OFFRE_ID, modification))
                    .isInstanceOf(EntrepriseNotFoundException.class);
        }
    }

    @Test
    void ouvrirUneOffreDejaOuverte_estIdempotent() {
        when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(1))));

        assertThat(service.changerStatut(ENTREPRISE_ID, OFFRE_ID, StatutOffre.OUVERTE).statut())
                .isEqualTo(StatutOffre.OUVERTE);
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
        void champsDeFiltre_sontNettoyesALEnregistrement() {
            when(entrepriseRepository.findById(ENTREPRISE_ID)).thenReturn(Optional.of(entreprise));
            when(offreRepository.save(any(Offre.class))).thenAnswer(invocation -> invocation.getArgument(0));
            OffreRequest saisie = new OffreRequest("Stage PFE Java", "API", "  Casa   blanca ", " Data ",
                    new LinkedHashSet<>(List.of("Java", "java ", "Spring  Boot")), 6, AUJOURDHUI.plusDays(30));

            OffreResponse reponse = service.creer(ENTREPRISE_ID, saisie);

            assertThat(reponse.ville()).isEqualTo("Casa blanca");
            assertThat(reponse.domaine()).isEqualTo("Data");
            assertThat(reponse.technologies()).containsExactly("Java", "Spring Boot");
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
