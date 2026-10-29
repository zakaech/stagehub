package com.stagehub.candidature.entity;

/**
 * État de l'analyse d'une candidature par l'IA (phase 8 e), tel que publié par ai-service. L'interface y ajoute un
 * état calculé à la lecture, DESACTIVEE, pour une analyse en attente quand l'IA est arrêtée (voir AnalyseIaResponse).
 */
public enum StatutAnalyse {
    EN_ATTENTE,
    EN_COURS,
    TERMINEE,
    ECHEC
}
