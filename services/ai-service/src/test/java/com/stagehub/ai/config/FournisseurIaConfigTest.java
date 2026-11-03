package com.stagehub.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Refus de démarrer avec OpenAI actif et sans clé. */
class FournisseurIaConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AnalyseProperties.class)
    static class Proprietes {
    }

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(Proprietes.class, FournisseurIaConfig.class);

    @Test
    void openAiActifSansCle_refuseDeDemarrer() {
        contexte.withPropertyValues("spring.ai.model.chat=openai", "stagehub.analyse.active=true",
                        "spring.ai.openai.api-key=")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("OPENAI_API_KEY doit être défini"));
    }

    @Test
    void openAiActifAvecCle_demarre() {
        contexte.withPropertyValues("spring.ai.model.chat=openai", "stagehub.analyse.active=true",
                        "spring.ai.openai.api-key=sk-test")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void openAiDesactive_demarreSansCle() {
        // Analyse arrêtée (recette Jenkins, IA_ACTIVE=false) : aucune clé exigée
        contexte.withPropertyValues("spring.ai.model.chat=openai", "stagehub.analyse.active=false")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void ollamaActif_demarreSansCleOpenAi() {
        contexte.withPropertyValues("spring.ai.model.chat=ollama", "stagehub.analyse.active=true")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
