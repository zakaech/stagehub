package com.stagehub.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Analyse des candidatures ({@code stagehub.analyse.*}, phase 8 d).
 *
 * @param active       tâche d'analyse en marche (faux par défaut : il faut un modèle Ollama ; recette Jenkins sans IA)
 * @param tentativesMax nombre de tentatives au plus par demande (panne passagère, réponse invalide du modèle)
 * @param attentes     attente avant la 2e, la 3e... tentative (la dernière valeur sert ensuite)
 * @param bail         une demande EN_COURS depuis plus longtemps est reprise (service arrêté pendant l'analyse)
 * @param ecartAlerte  écart (en points) entre le score du modèle et la part des technologies retrouvées au-delà
 *                     duquel l'entreprise est alertée
 */
@ConfigurationProperties("stagehub.analyse")
public record AnalyseProperties(boolean active, Integer tentativesMax, List<Duration> attentes, Duration bail,
                                Integer ecartAlerte) {

    public AnalyseProperties {
        tentativesMax = tentativesMax == null ? 3 : tentativesMax;
        attentes = attentes == null || attentes.isEmpty() ? List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)) : List.copyOf(attentes);
        bail = bail == null ? Duration.ofMinutes(15) : bail;
        ecartAlerte = ecartAlerte == null ? 50 : ecartAlerte;
    }

    /** Attente avant la tentative suivant la n-ième (n ≥ 1). */
    public Duration attenteApres(int tentative) {
        return attentes.get(Math.min(tentative, attentes.size()) - 1);
    }
}
