package com.stagehub.analyse.service;

import com.stagehub.analyse.event.AnalyseCandidature;
import com.stagehub.analyse.repository.EvenementTraiteRepository;
import com.stagehub.candidature.entity.AnalyseIa;
import com.stagehub.candidature.entity.Candidature;
import com.stagehub.candidature.entity.StatutAnalyse;
import com.stagehub.candidature.entity.StatutCandidature;
import com.stagehub.candidature.repository.CandidatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultatAnalyseServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-28T10:00:00Z");
    private static final UUID EVENEMENT = UUID.fromString("6034dc51-7a32-4179-8fa8-50c911afdbfd");

    @Mock
    private EvenementTraiteRepository evenementsTraites;
    @Mock
    private CandidatureRepository candidatures;

    private ResultatAnalyseService service;
    private Candidature candidature;

    @BeforeEach
    void creer() {
        service = new ResultatAnalyseService(evenementsTraites, candidatures, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        candidature = new Candidature();
        candidature.setId(12L);
        candidature.getAnalyse().setStatut(StatutAnalyse.EN_ATTENTE);
    }

    @Test
    void analyseTerminee_resultatEnregistre_statutDeLaCandidatureInchange() {
        nouvelEvenement();

        service.appliquer(EVENEMENT, new AnalyseCandidature(12L, "TERMINEE", null, null, 85, "Bon profil.",
                List.of("Étudiant DevOps", "Stage OCP"), List.of("Docker", "Jenkins"), 4, false, true, "qwen3.5:4b",
                MAINTENANT));

        AnalyseIa analyse = candidature.getAnalyse();
        assertThat(analyse.getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
        assertThat(analyse.getScore()).isEqualTo(85);
        assertThat(analyse.getResume()).containsExactly("Étudiant DevOps", "Stage OCP");
        assertThat(analyse.getTechnologiesTrouvees()).containsExactly("Docker", "Jenkins");
        assertThat(analyse.getTechnologiesOffre()).isEqualTo(4);
        assertThat(analyse.isContenuSuspect()).isTrue();
        assertThat(analyse.getDate()).isEqualTo(MAINTENANT);
        // Une aide, jamais une décision : le statut de la candidature ne bouge pas
        assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
    }

    @Test
    void enCoursPuisNouvelleTentativeProgrammee_puisEchec() {
        nouvelEvenement();
        Instant prochaine = MAINTENANT.plusSeconds(60);

        service.appliquer(EVENEMENT, etat("EN_COURS", null, null, MAINTENANT));
        assertThat(candidature.getAnalyse().getStatut()).isEqualTo(StatutAnalyse.EN_COURS);

        service.appliquer(UUID.randomUUID(), etat("EN_ATTENTE", null, prochaine, MAINTENANT.plusSeconds(1)));
        assertThat(candidature.getAnalyse().getStatut()).isEqualTo(StatutAnalyse.EN_ATTENTE);
        assertThat(candidature.getAnalyse().getProchaineTentative()).isEqualTo(prochaine);

        service.appliquer(UUID.randomUUID(), etat("ECHEC", "CV_ILLISIBLE", null, MAINTENANT.plusSeconds(2)));
        assertThat(candidature.getAnalyse().getStatut()).isEqualTo(StatutAnalyse.ECHEC);
        assertThat(candidature.getAnalyse().getMotifEchec()).isEqualTo("CV_ILLISIBLE");
        assertThat(candidature.getAnalyse().getProchaineTentative()).isNull();
        assertThat(candidature.getAnalyse().getScore()).isNull();
    }

    @Test
    void etatPlusAncienQueCeluiEnregistre_ignore() {
        nouvelEvenement();
        candidature.getAnalyse().setStatut(StatutAnalyse.TERMINEE);
        candidature.getAnalyse().setDate(MAINTENANT);

        service.appliquer(EVENEMENT, etat("EN_COURS", null, null, MAINTENANT.minusSeconds(30)));

        assertThat(candidature.getAnalyse().getStatut()).isEqualTo(StatutAnalyse.TERMINEE);
    }

    @Test
    void candidatureSupprimeeDepuis_resultatOrphelinIgnoreSansErreur() {
        when(evenementsTraites.inscrire(eq(EVENEMENT), any(), eq(MAINTENANT))).thenReturn(1);
        when(candidatures.findById(99L)).thenReturn(Optional.empty());

        service.appliquer(EVENEMENT, etat(99L, "TERMINEE"));

        // Rien à faire : l'événement est marqué traité (pas de rejeu), aucune exception (pas de topic d'erreurs)
        verify(candidatures).findById(99L);
    }

    @Test
    void evenementDejaTraite_ignore() {
        when(evenementsTraites.inscrire(eq(EVENEMENT), any(), eq(MAINTENANT))).thenReturn(0);

        service.appliquer(EVENEMENT, etat("TERMINEE", null, null, MAINTENANT));

        verify(candidatures, never()).findById(any());
    }

    private void nouvelEvenement() {
        when(evenementsTraites.inscrire(any(), any(), eq(MAINTENANT))).thenReturn(1);
        when(candidatures.findById(12L)).thenReturn(Optional.of(candidature));
    }

    private static AnalyseCandidature etat(String statut, String motif, Instant prochaine, Instant date) {
        return new AnalyseCandidature(12L, statut, motif, prochaine, null, null, null, null, null, false, false, null,
                date);
    }

    private static AnalyseCandidature etat(Long candidatureId, String statut) {
        return new AnalyseCandidature(candidatureId, statut, null, null, 70, "ok", List.of("x"), List.of(), 0, false,
                false, "m", MAINTENANT);
    }
}
