package com.stagehub.candidature.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatutCandidatureTest {

    /** Les 16 couples (départ, arrivée) : la table de vérité complète de la machine à états. */
    @ParameterizedTest(name = "{0} → {1} : {2}")
    @CsvSource({
            "EN_ATTENTE, EN_ATTENTE, false",
            "EN_ATTENTE, ENTRETIEN,  true",
            "EN_ATTENTE, ACCEPTEE,   false",
            "EN_ATTENTE, REFUSEE,    true",
            "ENTRETIEN,  EN_ATTENTE, false",
            "ENTRETIEN,  ENTRETIEN,  false",
            "ENTRETIEN,  ACCEPTEE,   true",
            "ENTRETIEN,  REFUSEE,    true",
            "ACCEPTEE,   EN_ATTENTE, false",
            "ACCEPTEE,   ENTRETIEN,  false",
            "ACCEPTEE,   ACCEPTEE,   false",
            "ACCEPTEE,   REFUSEE,    false",
            "REFUSEE,    EN_ATTENTE, false",
            "REFUSEE,    ENTRETIEN,  false",
            "REFUSEE,    ACCEPTEE,   false",
            "REFUSEE,    REFUSEE,    false",
    })
    void peutPasserA_respecteLaMachineAEtats(StatutCandidature depuis, StatutCandidature vers, boolean attendu) {
        assertThat(depuis.peutPasserA(vers)).isEqualTo(attendu);
    }

    @Test
    void seulsAccepteeEtRefuseeSontFinaux() {
        assertThat(StatutCandidature.ACCEPTEE.estFinal()).isTrue();
        assertThat(StatutCandidature.REFUSEE.estFinal()).isTrue();
        assertThat(StatutCandidature.EN_ATTENTE.estFinal()).isFalse();
        assertThat(StatutCandidature.ENTRETIEN.estFinal()).isFalse();
    }
}
