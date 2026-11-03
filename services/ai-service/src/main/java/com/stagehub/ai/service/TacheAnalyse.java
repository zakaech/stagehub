package com.stagehub.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche l'analyse des demandes en attente, une demande par passage, 10 secondes après la fin du
 * précédent ({@code fixedDelay} : deux analyses ne se chevauchent jamais dans une instance). Pas dans le consommateur
 * Kafka : une analyse dure plusieurs minutes, et un consommateur trop lent est exclu du groupe par Kafka
 * ({@code max.poll.interval.ms}), puis ses messages sont relivrés.
 * <p>Inactive par défaut ({@code stagehub.analyse.active}) : sans modèle (recette Jenkins, tests), les demandes
 * attendent simplement.
 */
@Component
@ConditionalOnProperty(name = "stagehub.analyse.active", havingValue = "true")
public class TacheAnalyse {

    private final AnalyseCandidatureService analyse;

    public TacheAnalyse(AnalyseCandidatureService analyse) {
        this.analyse = analyse;
    }

    @Scheduled(fixedDelayString = "${stagehub.analyse.intervalle:10s}", initialDelayString = "${stagehub.analyse.intervalle:10s}")
    public void analyserLaProchaineDemande() {
        analyse.traiterLaProchaine();
    }
}
