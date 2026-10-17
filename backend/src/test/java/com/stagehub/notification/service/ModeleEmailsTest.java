package com.stagehub.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeleEmailsTest {

    private final ModeleEmails modeles = new ModeleEmails(new DefaultResourceLoader());

    private static final Map<String, String> VARIABLES = Map.of(
            "prenom", "Sara",
            "offreTitre", "Stage PFE Java",
            "entrepriseNom", "TechNova",
            "nom", "TechNova",
            "lienCandidatures", "http://localhost:4200/etudiant/candidatures",
            "lienOffres", "http://localhost:4200/offres",
            "lienEspace", "http://localhost:4200/entreprise/offres");

    /** Chaque modèle existe, commence par "Objet :" et n'utilise que des variables connues. */
    @ParameterizedTest
    @ValueSource(strings = {"candidature-entretien", "candidature-acceptee", "candidature-refusee",
            "entreprise-validee", "entreprise-refusee"})
    void chaqueModele_seRendSansVariableRestante(String nom) {
        ModeleEmails.EmailRendu email = modeles.rendre(nom, VARIABLES);

        assertThat(email.objet()).isNotBlank().doesNotContain("${");
        assertThat(email.corps()).isNotBlank().doesNotContain("${").contains("L'équipe StageHub");
    }

    @Test
    void variablesRemplacees_dansLObjetEtLeCorps() {
        ModeleEmails.EmailRendu email = modeles.rendre("candidature-entretien", VARIABLES);

        assertThat(email.objet()).isEqualTo("Entretien proposé pour « Stage PFE Java »");
        assertThat(email.corps()).startsWith("Bonjour Sara,").contains("TechNova souhaite vous rencontrer");
    }

    @Test
    void variableManquante_echoueAuLieuDEnvoyerUnTexteIncomplet() {
        assertThatThrownBy(() -> modeles.rendre("candidature-entretien", Map.of("prenom", "Sara")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offreTitre");
    }

    @Test
    void modeleInexistant_estSignale() {
        assertThatThrownBy(() -> modeles.rendre("inconnu", VARIABLES)).hasMessageContaining("inconnu");
    }
}
