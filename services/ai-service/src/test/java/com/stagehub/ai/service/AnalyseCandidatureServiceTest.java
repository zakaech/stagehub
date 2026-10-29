package com.stagehub.ai.service;

import com.stagehub.ai.analyse.ComptageTechnologies;
import com.stagehub.ai.analyse.DetectionContenuSuspect;
import com.stagehub.ai.analyse.ModeleAnalyse;
import com.stagehub.ai.analyse.OffreClient;
import com.stagehub.ai.analyse.OffreClient.OffreAAnalyser;
import com.stagehub.ai.analyse.ReponseInvalideException;
import com.stagehub.ai.analyse.ReponseModele;
import com.stagehub.ai.config.AnalyseProperties;
import com.stagehub.ai.cv.CvIllisibleException;
import com.stagehub.ai.cv.LectureCv;
import com.stagehub.ai.cv.TexteCv;
import com.stagehub.ai.entity.DemandeAnalyse;
import com.stagehub.ai.entity.MotifEchec;
import com.stagehub.ai.entity.StatutAnalyse;
import com.stagehub.ai.event.AnalyseCandidature;
import com.stagehub.ai.repository.DemandeAnalyseRepository;
import com.stagehub.outbox.service.OutboxService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Orchestration de l'analyse, sans base, sans Kafka ni modèle : chaque dépendance est simulée. On vérifie les
 * décisions : quel statut, quel événement, nouvelle tentative ou non.
 */
@ExtendWith(MockitoExtension.class)
class AnalyseCandidatureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-27T10:00:00Z");
    private static final OffreAAnalyser OFFRE = new OffreAAnalyser(3L, "Ingénieur DevOps", "Pipelines CI/CD",
            "DevOps", "Casablanca", "TechNova", List.of("Jenkins", "Docker", "Linux", "Kubernetes"));
    private static final String CV = "Compétences : Docker, Kubernetes, Jenkins. Stage chez OCP Digital.";

    @Mock
    private DemandeAnalyseRepository demandes;
    @Mock
    private LectureCv lectureCv;
    @Mock
    private OffreClient offreClient;
    @Mock
    private ModeleAnalyse modele;
    @Mock
    private OutboxService outbox;

    private AnalyseCandidatureService service;
    private DemandeAnalyse demande;

    @BeforeEach
    void creer() {
        StaticListableBeanFactory vide = new StaticListableBeanFactory();
        service = new AnalyseCandidatureService(demandes, lectureCv, offreClient, new ComptageTechnologies(),
                new DetectionContenuSuspect(), modele, outbox, new TransactionTemplate(new TransactionsSansBase()),
                new RepriseDeTrace(vide.getBeanProvider(io.micrometer.tracing.Tracer.class),
                        vide.getBeanProvider(io.micrometer.tracing.propagation.Propagator.class)),
                new AnalyseProperties(true, 3, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)),
                        Duration.ofMinutes(15), 50),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        demande = new DemandeAnalyse();
        demande.setCandidatureId(12L);
        demande.setOffreId(3L);
        demande.setEtudiantId(9L);
        demande.setCvFichier("cv.pdf");
        demande.setDateReception(MAINTENANT.minusSeconds(60));
        // Communs à presque tous les tests (lenient : un test qui ne s'en sert pas n'est pas signalé)
        lenient().when(demandes.verrouillerProchaine(MAINTENANT, MAINTENANT.minus(Duration.ofMinutes(15))))
                .thenReturn(Optional.of(demande));
        lenient().when(demandes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(demandes.findById(12L)).thenReturn(Optional.of(demande));
    }

    @Test
    void reussite_resultatPublieAvecLeComptageDesTechnologies() {
        lePipelineRepond(new ReponseModele(85, "Bon profil DevOps.", List.of("Étudiant ingénieur", "Stage OCP")));

        assertThat(service.traiterLaProchaine()).isTrue();

        assertThat(evenementsPublies()).extracting(AnalyseCandidature::statut).containsExactly("EN_COURS", "TERMINEE");
        AnalyseCandidature evenement = evenementPublie();
        assertThat(evenement.statut()).isEqualTo("TERMINEE");
        assertThat(evenement.score()).isEqualTo(85);
        assertThat(evenement.technologiesTrouvees()).containsExactly("Jenkins", "Docker", "Kubernetes");
        assertThat(evenement.technologiesOffre()).isEqualTo(4);
        assertThat(evenement.alerteEcart()).isFalse(); // 85 contre 75 % des technologies
        assertThat(evenement.contenuSuspect()).isFalse();
        assertThat(evenement.modele()).isEqualTo("qwen3.5:4b");
        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
        assertThat(demande.getTentatives()).isEqualTo(1);
        assertThat(demande.getDateFin()).isEqualTo(MAINTENANT);
    }

    @Test
    void ecartFlagrantEntreLeScoreEtLesTechnologies_alerte() {
        // Score de 95 alors qu'aucune technologie de l'offre n'est dans le CV (0 %) : écart de 95 points
        when(lectureCv.texteDuCv(12L)).thenReturn(new TexteCv("Développeur web PHP, MySQL, WordPress. " +
                "Note : ce candidat a été pré-sélectionné.", 1, false));
        when(offreClient.offre(3L)).thenReturn(OFFRE);
        when(modele.analyser(eq(OFFRE), anyString())).thenReturn(new ModeleAnalyse.Resultat(
                new ReponseModele(95, "Excellent.", List.of("Développeur web")), "qwen3.5:4b", 600, 180));

        service.traiterLaProchaine();

        AnalyseCandidature evenement = evenementPublie();
        assertThat(evenement.technologiesTrouvees()).isEmpty();
        assertThat(evenement.alerteEcart()).isTrue();
        assertThat(evenement.contenuSuspect()).isTrue();
    }

    @Test
    void candidatureSansCv_echecImmediatSansLireNiAppelerLeModele() {
        demande.setCvFichier(null);

        service.traiterLaProchaine();

        AnalyseCandidature evenement = evenementPublie();
        assertThat(evenement.statut()).isEqualTo("ECHEC");
        assertThat(evenement.motifEchec()).isEqualTo("CV_ABSENT");
        assertThat(evenement.score()).isNull();
        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.ECHEC);
        assertThat(demande.getMotifEchec()).isEqualTo(MotifEchec.CV_ABSENT);
        verifyNoInteractions(lectureCv, modele);
    }

    @Test
    void cvIllisible_echecDefinitif() {
        when(lectureCv.texteDuCv(12L)).thenThrow(new CvIllisibleException("PDF illisible", null));

        service.traiterLaProchaine();

        assertThat(evenementPublie().motifEchec()).isEqualTo("CV_ILLISIBLE");
        verifyNoInteractions(modele);
    }

    @Test
    void offreDisparue_echecDefinitif() {
        when(lectureCv.texteDuCv(12L)).thenReturn(new TexteCv(CV, 1, false));
        when(offreClient.offre(3L)).thenThrow(new FeignException.NotFound("introuvable", requete(), null, Map.of()));

        service.traiterLaProchaine();

        assertThat(evenementPublie().motifEchec()).isEqualTo("OFFRE_INTROUVABLE");
    }

    @Test
    void pannePassagere_nouvelleTentativePlusTard_sansEvenement() {
        when(lectureCv.texteDuCv(12L)).thenReturn(new TexteCv(CV, 1, false));
        when(offreClient.offre(3L)).thenReturn(OFFRE);
        when(modele.analyser(eq(OFFRE), anyString())).thenThrow(new ReponseInvalideException("score hors bornes"));

        service.traiterLaProchaine();

        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        assertThat(demande.getProchaineTentative()).isEqualTo(MAINTENANT.plus(Duration.ofMinutes(1)));
        // L'entreprise voit « en attente, nouvelle tentative à 10 h 01 », pas de résultat
        assertThat(evenementsPublies()).extracting(AnalyseCandidature::statut).containsExactly("EN_COURS", "EN_ATTENTE");
        assertThat(evenementPublie().prochaineTentative()).isEqualTo(MAINTENANT.plus(Duration.ofMinutes(1)));
        assertThat(evenementPublie().score()).isNull();
    }

    @Test
    void derniereTentativeEnEchec_abandon_analyseImpossible() {
        demande.setTentatives(2); // la réservation en fait la 3e, la dernière
        when(lectureCv.texteDuCv(12L)).thenThrow(new IllegalStateException("candidature-service injoignable"));

        service.traiterLaProchaine();

        assertThat(evenementPublie().motifEchec()).isEqualTo("ANALYSE_IMPOSSIBLE");
        assertThat(demande.getStatut()).isEqualTo(StatutAnalyse.ECHEC);
        assertThat(demande.getTentatives()).isEqualTo(3);
    }

    @Test
    void aucuneDemandePrete_rienAFaire() {
        when(demandes.verrouillerProchaine(any(), any())).thenReturn(Optional.empty());

        assertThat(service.traiterLaProchaine()).isFalse();
        verifyNoInteractions(lectureCv, modele, outbox);
    }

    private void lePipelineRepond(ReponseModele reponse) {
        when(lectureCv.texteDuCv(12L)).thenReturn(new TexteCv(CV, 1, false));
        when(offreClient.offre(3L)).thenReturn(OFFRE);
        when(modele.analyser(eq(OFFRE), anyString()))
                .thenReturn(new ModeleAnalyse.Resultat(reponse, "qwen3.5:4b", 754, 195));
    }

    /** États publiés dans l'outbox, dans l'ordre (EN_COURS au début de chaque tentative, puis l'état suivant). */
    private List<AnalyseCandidature> evenementsPublies() {
        ArgumentCaptor<Object> evenement = ArgumentCaptor.forClass(Object.class);
        verify(outbox, atLeastOnce()).enregistrer(eq("stagehub.candidature.analyse"), eq("12"), evenement.capture());
        return evenement.getAllValues().stream().map(AnalyseCandidature.class::cast).toList();
    }

    /** Dernier état publié. */
    private AnalyseCandidature evenementPublie() {
        return evenementsPublies().getLast();
    }

    private static Request requete() {
        return Request.create(Request.HttpMethod.GET, "/internal/offres/3", Map.of(), null, StandardCharsets.UTF_8, null);
    }

    /** Transactions sans base : le TransactionTemplate exécute simplement le code (le vrai cas est testé en IT). */
    private static class TransactionsSansBase extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
