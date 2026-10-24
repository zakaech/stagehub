package com.stagehub.candidature.service;

import com.stagehub.candidature.client.OffreClient;
import com.stagehub.candidature.client.OffreInterne;
import com.stagehub.candidature.dto.CandidatureEntrepriseResponse;
import com.stagehub.candidature.dto.CandidatureEtudiantResponse;
import com.stagehub.candidature.dto.CandidatureRequest;
import com.stagehub.candidature.dto.OffreResumeResponse;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.event.CandidatureCreeeEvent;
import com.stagehub.candidature.event.CandidatureStatutChangeEvent;
import com.stagehub.candidature.mapper.CandidatureMapper;
import com.stagehub.candidature.repository.CandidatureRepository;
import com.stagehub.commons.dto.PageResponse;
import com.stagehub.common.exception.AccesNonAutoriseException;
import com.stagehub.common.exception.CandidatureDejaExistanteException;
import com.stagehub.common.exception.CandidatureNotFoundException;
import com.stagehub.common.exception.OffreNonDisponibleException;
import com.stagehub.common.exception.OffreNotFoundException;
import com.stagehub.common.exception.ServiceIndisponibleException;
import com.stagehub.common.exception.TransitionStatutInvalideException;
import com.stagehub.common.exception.UtilisateurNotFoundException;
import com.stagehub.etudiant.entity.Etudiant;
import com.stagehub.etudiant.repository.EtudiantRepository;
import com.stagehub.common.Topics;
import com.stagehub.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    private static final Long ETUDIANT_ID = 1L;
    private static final Long ENTREPRISE_ID = 2L;
    private static final Long AUTRE_ENTREPRISE_ID = 3L;
    private static final Long OFFRE_ID = 10L;
    private static final Long CANDIDATURE_ID = 100L;

    @Mock
    private CandidatureRepository candidatureRepository;
    @Mock
    private EtudiantRepository etudiantRepository;
    @Mock
    private OffreClient offreClient;
    @Mock
    private OutboxService outbox;

    private CandidatureService service;
    private Etudiant etudiant;

    @BeforeEach
    void setUp() {
        // Vrai mapper généré par MapStruct : on teste aussi le contenu des réponses
        CandidatureMapper mapper = Mappers.getMapper(CandidatureMapper.class);
        // Vraie classe d'enregistrement (sans proxy transactionnel) : seuls les dépôts et offre-service sont simulés
        EnregistrementCandidature enregistrement =
                new EnregistrementCandidature(candidatureRepository, etudiantRepository, outbox);
        service = new CandidatureService(candidatureRepository, enregistrement, offreClient, mapper, outbox);

        etudiant = new Etudiant();
        etudiant.setId(ETUDIANT_ID);
        etudiant.setEmail("sara@etu.ma");
        etudiant.setNom("Alaoui");
        etudiant.setPrenom("Sara");
    }

    @Nested
    class Postuler {

        @Test
        void offreOuverte_creeUneCandidatureEnAttenteAvecLInstantaneDeLOffre() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
            enregistrementRenvoieLaCandidature();

            CandidatureEtudiantResponse reponse =
                    service.postuler(ETUDIANT_ID, OFFRE_ID, new CandidatureRequest("Très motivée"));

            ArgumentCaptor<Candidature> captor = ArgumentCaptor.forClass(Candidature.class);
            verify(candidatureRepository).saveAndFlush(captor.capture());
            Candidature enregistree = captor.getValue();
            assertThat(enregistree.getEtudiant()).isSameAs(etudiant);
            assertThat(enregistree.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
            // Instantané de l'offre recopié : propriété vérifiable localement, email et repli sans offre-service
            assertThat(enregistree.getOffreId()).isEqualTo(OFFRE_ID);
            assertThat(enregistree.getEntrepriseId()).isEqualTo(ENTREPRISE_ID);
            assertThat(enregistree.getOffreTitre()).isEqualTo("Stage PFE Java");
            assertThat(enregistree.getEntrepriseNom()).isEqualTo("Acme");
            assertThat(enregistree.getOffreVille()).isEqualTo("Casablanca");

            assertThat(reponse.offre()).isEqualTo(
                    new OffreResumeResponse(OFFRE_ID, "Stage PFE Java", "Acme", "Casablanca", "OUVERTE"));
            assertThat(reponse.lettreMotivation()).isEqualTo("Très motivée");
            assertThat(reponse.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        }

        @Test
        void publieCandidatureCreeePourAiService() {
            etudiant.setCheminCv("cv-en-vigueur.pdf");
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
            enregistrementRenvoieLaCandidature();

            service.postuler(ETUDIANT_ID, OFFRE_ID, null);

            verify(outbox).enregistrer(Topics.CANDIDATURE_CREEE, CANDIDATURE_ID.toString(),
                    new CandidatureCreeeEvent(CANDIDATURE_ID, OFFRE_ID, ETUDIANT_ID, "cv-en-vigueur.pdf"));
        }

        @Test
        void figeLeCvEnVigueurAuMomentDePostuler() {
            etudiant.setCheminCv("cv-en-vigueur.pdf");
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
            enregistrementRenvoieLaCandidature();

            service.postuler(ETUDIANT_ID, OFFRE_ID, null);

            ArgumentCaptor<Candidature> captor = ArgumentCaptor.forClass(Candidature.class);
            verify(candidatureRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getCvFichier()).isEqualTo("cv-en-vigueur.pdf");
        }

        @Test
        void sansCvNiLettre_estAcceptee() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
            enregistrementRenvoieLaCandidature();

            CandidatureEtudiantResponse reponse = service.postuler(ETUDIANT_ID, OFFRE_ID, null);

            ArgumentCaptor<Candidature> captor = ArgumentCaptor.forClass(Candidature.class);
            verify(candidatureRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getCvFichier()).isNull();
            assertThat(reponse.lettreMotivation()).isNull();
        }

        @Test
        void offreQuiNAccepteplusDeCandidatures_leveOffreNonDisponibleSansRienEcrire() {
            // Fermée ou date limite dépassée : la règle est évaluée par offre-service (accepteCandidatures)
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(false));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(OffreNonDisponibleException.class);
            verifyNoInteractions(candidatureRepository, etudiantRepository);
        }

        @Test
        void offreInexistante_leveOffreNotFound() {
            // Traduit par OffreClientRepli à partir du 404 d'offre-service
            when(offreClient.offre(OFFRE_ID)).thenThrow(new OffreNotFoundException(OFFRE_ID));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(OffreNotFoundException.class);
            verifyNoInteractions(candidatureRepository);
        }

        @Test
        void offreServiceIndisponible_leve503SansRienEcrire() {
            when(offreClient.offre(OFFRE_ID)).thenThrow(new ServiceIndisponibleException("indisponible"));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(ServiceIndisponibleException.class);
            verifyNoInteractions(candidatureRepository, etudiantRepository);
        }

        @Test
        void dejaPostule_leveCandidatureDejaExistante() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(candidatureRepository.existsByEtudiantIdAndOffreId(ETUDIANT_ID, OFFRE_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(CandidatureDejaExistanteException.class);
            verify(candidatureRepository, never()).saveAndFlush(any());
        }

        @Test
        void doublonConcurrent_rejeteParLaContrainteUnique_leveCandidatureDejaExistante() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.of(etudiant));
            when(candidatureRepository.saveAndFlush(any(Candidature.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_candidature_etudiant_offre"));

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(CandidatureDejaExistanteException.class);
        }

        @Test
        void etudiantInexistant_leve404() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(etudiantRepository.findAvecVerrou(ETUDIANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.postuler(ETUDIANT_ID, OFFRE_ID, null))
                    .isInstanceOf(UtilisateurNotFoundException.class);
        }
    }

    @Nested
    class ListerCandidaturesEtudiant {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        void afficheLesResumesAJourLusEnUnSeulAppel() {
            Candidature c1 = candidature(CANDIDATURE_ID, OFFRE_ID);
            Candidature c2 = candidature(CANDIDATURE_ID + 1, OFFRE_ID + 1);
            when(candidatureRepository.findByEtudiantId(ETUDIANT_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(c1, c2), pageable, 2));
            OffreResumeResponse aJour = new OffreResumeResponse(OFFRE_ID, "Titre modifié", "Acme", "Rabat", "FERMEE");
            OffreResumeResponse autre = new OffreResumeResponse(OFFRE_ID + 1, "Autre", "Acme", "Fès", "OUVERTE");
            when(offreClient.resumes(Set.of(OFFRE_ID, OFFRE_ID + 1))).thenReturn(List.of(aJour, autre));

            PageResponse<CandidatureEtudiantResponse> page = service.listerCandidaturesEtudiant(ETUDIANT_ID, pageable);

            assertThat(page.content()).extracting(CandidatureEtudiantResponse::offre).containsExactly(aJour, autre);
        }

        @Test
        void offreAbsenteDesResumes_afficheLInstantaneAvecUnStatutInconnu() {
            // Repli : offre-service indisponible (liste vide renvoyée par OffreClientRepli) ou offre supprimée
            when(candidatureRepository.findByEtudiantId(ETUDIANT_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(candidature(CANDIDATURE_ID, OFFRE_ID)), pageable, 1));
            when(offreClient.resumes(Set.of(OFFRE_ID))).thenReturn(List.of());

            PageResponse<CandidatureEtudiantResponse> page = service.listerCandidaturesEtudiant(ETUDIANT_ID, pageable);

            assertThat(page.content().getFirst().offre())
                    .isEqualTo(new OffreResumeResponse(OFFRE_ID, "Stage PFE Java", "Acme", "Casablanca", null));
        }

        @Test
        void aucuneCandidature_nAppellePasOffreService() {
            when(candidatureRepository.findByEtudiantId(ETUDIANT_ID, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            assertThat(service.listerCandidaturesEtudiant(ETUDIANT_ID, pageable).content()).isEmpty();
            verifyNoInteractions(offreClient);
        }
    }

    @Nested
    class ListerCandidaturesOffre {

        @Test
        void offreDUneAutreEntreprise_leveAccesNonAutorise() {
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));

            assertThatThrownBy(() -> service.listerCandidaturesOffre(AUTRE_ENTREPRISE_ID, OFFRE_ID, Pageable.unpaged()))
                    .isInstanceOf(AccesNonAutoriseException.class);
            verifyNoInteractions(candidatureRepository);
        }

        @Test
        void offreInexistante_leve404() {
            when(offreClient.offre(OFFRE_ID)).thenThrow(new OffreNotFoundException(OFFRE_ID));

            assertThatThrownBy(() -> service.listerCandidaturesOffre(ENTREPRISE_ID, OFFRE_ID, Pageable.unpaged()))
                    .isInstanceOf(OffreNotFoundException.class);
        }

        @Test
        void parLEntrepriseProprietaire_renvoieLesCandidatures() {
            Pageable pageable = PageRequest.of(0, 10);
            when(offreClient.offre(OFFRE_ID)).thenReturn(offre(true));
            when(candidatureRepository.findByOffreId(OFFRE_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(candidature(CANDIDATURE_ID, OFFRE_ID)), pageable, 1));

            PageResponse<CandidatureEntrepriseResponse> page =
                    service.listerCandidaturesOffre(ENTREPRISE_ID, OFFRE_ID, pageable);

            assertThat(page.content()).singleElement().satisfies(c -> {
                assertThat(c.offreTitre()).isEqualTo("Stage PFE Java");
                assertThat(c.etudiantEmail()).isEqualTo("sara@etu.ma");
            });
        }
    }

    @Nested
    class ChangerStatut {

        @Test
        void parLEntrepriseProprietaire_appliqueLaTransitionSansAppelerOffreService() {
            Candidature candidature = candidature(CANDIDATURE_ID, OFFRE_ID);
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            CandidatureEntrepriseResponse reponse =
                    service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN);

            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.ENTRETIEN);
            assertThat(reponse.statut()).isEqualTo(StatutCandidature.ENTRETIEN);
            assertThat(reponse.etudiantEmail()).isEqualTo("sara@etu.ma");
            // L'événement (écrit dans l'outbox, clé = id de la candidature) porte tout ce qu'il faut pour l'email
            verify(outbox).enregistrer(Topics.CANDIDATURE_STATUT_CHANGE, CANDIDATURE_ID.toString(),
                    new CandidatureStatutChangeEvent(CANDIDATURE_ID, "sara@etu.ma", "Sara",
                    "Stage PFE Java", "Acme", StatutCandidature.EN_ATTENTE, StatutCandidature.ENTRETIEN));
            // La propriété se vérifie avec l'entrepriseId recopié : aucune dépendance à offre-service
            verifyNoInteractions(offreClient);
        }

        @Test
        void parUneAutreEntreprise_leveAccesNonAutoriseSansModifier() {
            Candidature candidature = candidature(CANDIDATURE_ID, OFFRE_ID);
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            assertThatThrownBy(() -> service.changerStatut(AUTRE_ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN))
                    .isInstanceOf(AccesNonAutoriseException.class);
            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
            verifyNoInteractions(outbox);
        }

        @Test
        void transitionInvalide_leveTransitionStatutInvalide() {
            Candidature candidature = candidature(CANDIDATURE_ID, OFFRE_ID);
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.of(candidature));

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ACCEPTEE))
                    .isInstanceOf(TransitionStatutInvalideException.class);
            assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
            verifyNoInteractions(outbox);
        }

        @Test
        void candidatureInexistante_leveCandidatureNotFound() {
            when(candidatureRepository.findById(CANDIDATURE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.changerStatut(ENTREPRISE_ID, CANDIDATURE_ID, StatutCandidature.ENTRETIEN))
                    .isInstanceOf(CandidatureNotFoundException.class);
        }
    }

    // --- Fabriques de données de test ---

    /** Comme JPA : saveAndFlush attribue un id et renvoie l'entité enregistrée. */
    private void enregistrementRenvoieLaCandidature() {
        when(candidatureRepository.saveAndFlush(any(Candidature.class))).thenAnswer(appel -> {
            Candidature candidature = appel.getArgument(0);
            candidature.setId(CANDIDATURE_ID);
            return candidature;
        });
    }

    /** Offre telle que renvoyée par offre-service. */
    private static OffreInterne offre(boolean accepteCandidatures) {
        return new OffreInterne(OFFRE_ID, "Stage PFE Java", "Casablanca", accepteCandidatures ? "OUVERTE" : "FERMEE",
                LocalDate.of(2026, 12, 31), ENTREPRISE_ID, "Acme", accepteCandidatures);
    }

    private Candidature candidature(Long id, Long offreId) {
        Candidature candidature = new Candidature();
        candidature.setId(id);
        candidature.setEtudiant(etudiant);
        candidature.setOffreId(offreId);
        candidature.setEntrepriseId(ENTREPRISE_ID);
        candidature.setOffreTitre("Stage PFE Java");
        candidature.setEntrepriseNom("Acme");
        candidature.setOffreVille("Casablanca");
        return candidature;
    }
}
