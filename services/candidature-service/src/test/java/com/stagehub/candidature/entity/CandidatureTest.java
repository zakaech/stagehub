package com.stagehub.candidature.entity;

import com.stagehub.common.exception.TransitionStatutInvalideException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidatureTest {

    @Test
    void nouvelleCandidature_estEnAttente() {
        assertThat(new Candidature().getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
    }

    @Test
    void changerStatut_parcoursValide_jusquAAcceptee() {
        Candidature candidature = new Candidature();

        candidature.changerStatut(StatutCandidature.ENTRETIEN);
        candidature.changerStatut(StatutCandidature.ACCEPTEE);

        assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.ACCEPTEE);
    }

    @Test
    void changerStatut_transitionInvalide_leveExceptionEtNeModifiePasLeStatut() {
        Candidature candidature = new Candidature();

        assertThatThrownBy(() -> candidature.changerStatut(StatutCandidature.ACCEPTEE))
                .isInstanceOf(TransitionStatutInvalideException.class)
                .hasMessageContaining("EN_ATTENTE → ACCEPTEE");
        assertThat(candidature.getStatut()).isEqualTo(StatutCandidature.EN_ATTENTE);
    }
}
