package com.stagehub.ai.entity;

/**
 * Cycle de vie d'une demande d'analyse (phase 8 d) : EN_ATTENTE → EN_COURS → TERMINEE, ou ECHEC. Après une panne
 * passagère, la demande repasse EN_ATTENTE (avec une date de prochaine tentative) tant qu'il reste des tentatives.
 */
public enum StatutAnalyse {
    EN_ATTENTE,
    EN_COURS,
    TERMINEE,
    ECHEC
}
