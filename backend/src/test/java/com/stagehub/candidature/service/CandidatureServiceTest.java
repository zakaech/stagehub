package com.stagehub.candidature.service;

import com.stagehub.auth.entity.Utilisateur;
import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.dto.CandidatureRequest;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.mapper.CandidatureMapper;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureDejaExistanteException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.OffreNonDisponibleException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.entreprise.entity.Entreprise;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.offre.dto.OffreResumeResponse;
import com.stagehub.offre.entity.Offre;
import com.stagehub.offre.entity.StatutOffre;
import com.stagehub.offre.repository.OffreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 6, 1);
    private static final Long ETUDIANT_ID = 1L;
    private static final Long ENTREPRISE_ID = 2L;
    private static final Long AUTRE_ENTREPRISE_ID = 3L;
    private static final Long OFFRE_ID = 10L;
    private static final Long CANDIDATURE_ID = 100L;

    @Mock
    private CandidatureRepository candidatureRepository;
    @Mock
    private OffreRepository offreRepository;
    @Mock
    private EtudiantRepository etudiantRepository;

    private CandidatureService service;
    private Etudiant etudiant;
    private Entreprise entreprise;

    @BeforeEach
    void setUp() {
        // Horloge figée : les règles de date limite deviennent déterministes
        Clock clock = Clock.fixed(AUJOURDHUI.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        // Vrai mapper généré par MapStruct : on teste aussi le contenu des réponses
        CandidatureMapper mapper = Mappers.getMapper(CandidatureMapper.class);
        service = new CandidatureService(candidatureRepository, offreRepository, etudiantRepository, mapper, clock);

        etudiant = etudiant(ETUDIANT_ID, "sara@etu.ma");
        entreprise = entreprise(ENTREPRISE_ID, "Acme");
    }

    @Nested
    class Postuler {

        @Test
        void offreOuverte_creeUneCandidatureEnAttente() {
            Offre offre = offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10));
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre));
            when(candidatureRepository.existsByEtudiantIdAndOffreId(ETUDIANT_ID, OFFRE_ID)).thenReturn(false);
            when(etudiantRepository.getReferenceById(ETUDIANT_ID)).thenReturn(etudiant);

            CandidatureEtudiantResponse reponse =
                    service.postuler(ETUDIANT_ID, OFFRE_ID, new CandidatureRequest("Très motivée"));

            ArgumentCaptor<Candidature> captor = ArgumentCaptor.forClass(Candidature.class);
            verify(candidatureRepository).saveAndFlush(captor.capture());
            Candidature enregistree = captor.getValue();
            assertThat(enregistree.getEtudiant()).isSameAs(etudiant);
            assertThat(enregistree.getOffre()).isSameAs(offre);
            assertThat(enregistree.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);

            assertThat(reponse.offre()).isEqualTo(
                    new OffreResumeResponse(OFFRE_ID, "Stage PFE Java", "Acme", "Casablanca", StatutOffre.OUVERTE));
            assertThat(reponse.lettreMotivation()).isEqualTo("Très motivée");
            assertThat(reponse.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        }

        @Test
        void sansLettreDeMotivation_estAcceptee() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));
            when(etudiantRepository.getReferenceById(ETUDIANT_ID)).thenReturn(etudiant);

            CandidatureEtudiantResponse reponse = service.postuler(ETUDIANT_ID, OFFRE_ID, null);

            assertThat(reponse.lettreMotivation()).isNull();
        }

        @Test
        void dateLimiteAujourdhui_estEncoreAcceptee() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI)));
            when(etudiantRepository.getReferenceById(ETUDIANT_ID)).thenReturn(etudiant);

            service.postuler(ETUDIANT_ID, OFFRE_ID, null);

            verify(candidatureRepository).saveAndFlush(any(Candidature.class));
        }

        @Test
        void dateLimiteDepassee_leveOffreNonDisponible() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.minusDays(1))));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(OffreNonDisponibleException.class);
            verify(candidatureRepository, never()).saveAndFlush(any());
        }

        @Test
        void offreFermee_leveOffreNonDisponible() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.FERMEE, AUJOURDHUI.plusDays(10))));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(OffreNonDisponibleException.class);
            verify(candidatureRepository, never()).saveAndFlush(any());
        }

        @Test
        void offreInexistante_leveOffreNotFound() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(OffreNotFoundException.class);
        }

        @Test
        void dejaPostule_leveCandidatureDejaExistante() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));
            when(candidatureRepository.existsByEtudiantIdAndOffreId(ETUDIANT_ID, OFFRE_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(CandidatureDejaExistanteException.class);
            verify(candidatureRepository, never()).saveAndFlush(any());
        }

        @Test
        void doublonConcurrent_rejeteParLaContrainteUnique_leveCandidatureDejaExistante() {
            when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));
            when(etudiantRepository.getReferenceById(ETUDIANT_ID)).thenReturn(etudiant);
            when(candidatureRepository.saveAndFlush(any(Candidature.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_candidature_etudiant_offre"));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(CandidatureDejaExistanteException.class);
        }
    }

    @Nested
    class ChangerStatut {

        @Test
        void parLEntrepriseProprietaire_appliqueLaTransition() {
            Candidature candidature = candidature(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10)));
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            CandidatureEntrepriseResponse reponse =
                    service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN);

            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.ENTRETIEN);
            assertThat(reponse.statut()).isEqualTo(StatutCandidature.ENTRETIEN);
            assertThat(reponse.etudiantEmail()).isEqualTo("sara@etu.ma");
        }

        @Test
        void parUneAutreEntreprise_leveAccesNonAutoriseSansModifier() {
            Candidature candidature = candidature(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10)));
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            assertThatThrownBy(() -> service.changerStatut(AUTRE_ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN))
                    .isInstanceOf(AccesNonAutoriseException.class);
            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        }

        @Test
        void transitionInvalide_leveTransitionStatutInvalide() {
            Candidature candidature = candidature(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10)));
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ACCEPTEE))
                    .isInstanceOf(TransitionStatutInvalideException.class);
            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        }

        @Test
        void candidatureInexistante_leveCandidatureNotFound() {
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN))
                    .isInstanceOf(CandidatureNotFoundException.class);
        }
    }

    @Test
    void listerCandidaturesOffre_dUneAutreEntreprise_leveAccesNonAutorise() {
        when(offreRepository.findById(OFFRE_ID)).thenReturn(Optional.of(offre(StatutOffre.OUVERTE, AUJOURDHUI.plusDays(10))));

        assertThatThrownBy(() -> service.listerCandidaturesOffre(AUTRE_ENTREPRISE_ID, OFFRE_ID, Pageable.unpaged()))
                .isInstanceOf(AccesNonAutoriseException.class);
        verifyNoInteractions(candidatureRepository);
    }

    // --- Fabriques de données de test ---

    private static Etudiant etudiant(Long id, String email) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId(id);
        utilisateur.setEmail(email);
        Etudiant etudiant = new Etudiant();
        etudiant.setId(id);
        etudiant.setUtilisateur(utilisateur);
        etudiant.setNom("Alaoui");
        etudiant.setPrenom("Sara");
        return etudiant;
    }

    private static Entreprise entreprise(Long id, String nom) {
        Entreprise entreprise = new Entreprise();
        entreprise.setId(id);
        entreprise.setNom(nom);
        return entreprise;
    }

    private Offre offre(StatutOffre statut, LocalDate dateLimite) {
        Offre offre = new Offre();
        offre.setId(OFFRE_ID);
        offre.setTitre("Stage PFE Java");
        offre.setVille("Casablanca");
        offre.setEntreprise(entreprise);
        offre.setStatut(statut);
        offre.setDateLimite(dateLimite);
        return offre;
    }

    private Candidature candidature(Offre offre) {
        Candidature candidature = new Candidature();
        candidature.setId(CANDIDATURE_ID);
        candidature.setEtudiant(etudiant);
        candidature.setOffre(offre);
        return candidature;
    }
}
