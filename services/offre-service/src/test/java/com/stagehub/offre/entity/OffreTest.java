package com.stagehub.offre.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OffreTest {

    @Test
    void normaliserChampsDeFiltre_retireLesEspacesEtDedoublonneSansTenirCompteDeLaCasse() {
        Offre offre = new Offre();
        offre.setVille("  Casa   blanca ");
        offre.setDomaine("Développement  web\t");
        offre.getTechnologies().addAll(List.of("Java", " java ", "Spring   Boot", "  ", "SQL"));

        offre.normaliserChampsDeFiltre();

        assertThat(offre.getVille()).isEqualTo("Casa blanca");
        assertThat(offre.getDomaine()).isEqualTo("Développement web");
        // Ordre de saisie conservé, première orthographe gardée, valeur vide écartée
        assertThat(offre.getTechnologies()).containsExactly("Java", "Spring Boot", "SQL");
    }
}
