package com.stagehub.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Modèle simulé pour les tests (phase 8 d) : Jenkins n'a pas d'Ollama. Il renvoie les réponses prévues par le test,
 * dans l'ordre, et garde les prompts reçus pour qu'on puisse vérifier ce que l'application envoie vraiment au modèle.
 * Options par défaut d'Ollama : comme le vrai, il accepte un schéma de sortie (sortie structurée native).
 */
public class ModeleSimule implements ChatModel {

    private final Deque<String> reponses = new ConcurrentLinkedDeque<>();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile CountDownLatch retenue;

    public void repondra(String... json) {
        reponses.addAll(List.of(json));
    }

    /** Le prochain appel attend que le test libère ce verrou (30 s au plus), comme une analyse qui dure. */
    public void retenir(CountDownLatch verrou) {
        retenue = verrou;
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    public void reinitialiser() {
        reponses.clear();
        prompts.clear();
        retenue = null;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        CountDownLatch verrou = retenue;
        if (verrou != null) {
            try {
                verrou.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String json = reponses.poll();
        if (json == null) {
            throw new IllegalStateException("Modèle simulé : aucune réponse prévue par le test");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))),
                ChatResponseMetadata.builder().model("modele-simule").usage(new DefaultUsage(600, 150)).build());
    }

    /**
     * Comme OllamaChatModel : des options propres au modèle, qui acceptent un schéma de sortie. Avec les options
     * génériques par défaut, Spring AI ne transmettrait pas le schéma (premier essai de AnalyseIT).
     */
    @Override
    public ChatOptions getOptions() {
        return OllamaChatOptions.builder().build();
    }

    /** Remplace le modèle d'Ollama dans le contexte des tests (spring.ai.model.chat=none). */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        ModeleSimule modeleSimule() {
            return new ModeleSimule();
        }
    }
}
