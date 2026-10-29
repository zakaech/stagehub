package com.stagehub.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fournisseur du modèle (spec §1) : Ollama en local par défaut, OpenAI par la configuration ({@code IA_FOURNISSEUR=openai}).
 * Vérifié au démarrage : avec OpenAI et l'analyse active, une clé d'API est obligatoire. Sans elle, le service refuse de
 * démarrer, plutôt que d'échouer à chaque analyse (401, nouvelles tentatives, puis « analyse impossible »).
 */
@Configuration(proxyBeanMethods = false)
public class FournisseurIaConfig {

    private static final Logger log = LoggerFactory.getLogger(FournisseurIaConfig.class);
    static final String OPENAI = "openai";

    public FournisseurIaConfig(Environment environnement, AnalyseProperties analyse) {
        String fournisseur = environnement.getProperty("spring.ai.model.chat", "ollama");
        verifier(fournisseur, analyse.active(), environnement.getProperty("spring.ai.openai.api-key", ""));
        log.info("Analyse des candidatures {} ; fournisseur du modèle : {}{}", analyse.active() ? "active" : "désactivée",
                fournisseur, OPENAI.equalsIgnoreCase(fournisseur) ? " (les CV sont envoyés à OpenAI)" : "");
    }

    static void verifier(String fournisseur, boolean active, String cleOpenAi) {
        if (active && OPENAI.equalsIgnoreCase(fournisseur) && (cleOpenAi == null || cleOpenAi.isBlank())) {
            throw new IllegalStateException("IA_FOURNISSEUR=openai avec IA_ACTIVE=true : OPENAI_API_KEY doit être "
                    + "défini (clé d'API OpenAI, dans .env, jamais dans le code)");
        }
    }
}
