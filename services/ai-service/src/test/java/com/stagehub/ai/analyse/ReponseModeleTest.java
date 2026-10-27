package com.stagehub.ai.analyse;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReponseModeleTest {

    private static final List<String> RESUME = List.of("Étudiant ingénieur en systèmes et réseaux");

    @Test
    void reponseUtilisable_aucuneErreur() {
        assertThat(new ReponseModele(0, "Profil sans rapport.", RESUME).erreurs()).isEmpty();
        assertThat(new ReponseModele(100, "Profil complet.", Collections.nCopies(5, "ligne")).erreurs()).isEmpty();
    }

    @Test
    void scoreAbsentOuHorsBornes_refuse() {
        assertThat(new ReponseModele(101, "ok", RESUME).erreurs()).hasSize(1).first().asString().contains("score");
        assertThat(new ReponseModele(-1, "ok", RESUME).erreurs()).hasSize(1);
        assertThat(new ReponseModele(null, "ok", RESUME).erreurs()).hasSize(1);
    }

    @Test
    void textesVidesOuTropLongs_refuses() {
        assertThat(new ReponseModele(50, " ", RESUME).erreurs()).hasSize(1);
        assertThat(new ReponseModele(50, "x".repeat(601), RESUME).erreurs()).hasSize(1);
        assertThat(new ReponseModele(50, "ok", List.of()).erreurs()).hasSize(1);
        assertThat(new ReponseModele(50, "ok", Collections.nCopies(6, "ligne")).erreurs()).hasSize(1);
        assertThat(new ReponseModele(50, "ok", List.of("x".repeat(301))).erreurs()).hasSize(1);
        assertThat(new ReponseModele(50, "ok", null).erreurs()).hasSize(1);
    }
}
