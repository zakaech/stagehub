package com.stagehub.ai.analyse;

import com.stagehub.ai.analyse.ComptageTechnologies.Correspondance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComptageTechnologiesTest {

    private final ComptageTechnologies comptage = new ComptageTechnologies();

    @Test
    void technologiesRetrouvees_dansLOrdreDeLOffre_sansTenirCompteDeLaCasseNiDesAccents() {
        Correspondance c = comptage.compter(List.of("Jenkins", "Docker", "Linux", "Kubernetes"),
                "Compétences : DOCKER, kubernetes (minikube), pipelines jenkins");

        assertThat(c.trouvees()).containsExactly("Jenkins", "Docker", "Kubernetes");
        assertThat(c.total()).isEqualTo(4);
        assertThat(c.pourcentage()).isEqualTo(75);
    }

    @Test
    void motEntierSeulement_javaNeCompteNiDansJavascriptNiDansUnAutreMot() {
        Correspondance c = comptage.compter(List.of("Java", "Go", "C++", "Node.js"),
                "JavaScript, Google Cloud, algorithmes en C++ et Node.js");

        assertThat(c.trouvees()).containsExactly("C++", "Node.js");
    }

    @Test
    void accentsDansLOffreEtDansLeCv() {
        Correspondance c = comptage.compter(List.of("Sécurité réseau"), "Modules : securite RESEAU et cryptographie");

        assertThat(c.trouvees()).containsExactly("Sécurité réseau");
    }

    @Test
    void offreSansTechnologie_pasDePourcentage() {
        Correspondance c = comptage.compter(List.of(), "Docker");

        assertThat(c.total()).isZero();
        assertThat(c.pourcentage()).isNull();
    }

    @Test
    void doublonsDeLOffre_comptesUneSeuleFois() {
        Correspondance c = comptage.compter(List.of("Docker", "docker", " Docker "), "Docker");

        assertThat(c.total()).isEqualTo(1);
        assertThat(c.pourcentage()).isEqualTo(100);
    }

    @Test
    void limiteConnue_lesVariantesNeSontPasReconnues() {
        // « K8s » pour Kubernetes : un repère simple, pas un dictionnaire de synonymes (documenté)
        Correspondance c = comptage.compter(List.of("Kubernetes"), "Déploiements K8s en production");

        assertThat(c.trouvees()).isEmpty();
    }
}
