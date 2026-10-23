package com.stagehub.entreprise.entity;

/**
 * Statut de validation d'un compte entreprise par l'admin.
 * Seul EN_ATTENTE peut évoluer ; VALIDEE et REFUSEE sont des décisions définitives.
 */
public enum StatutValidation {
    EN_ATTENTE,
    VALIDEE,
    REFUSEE;

    public boolean peutPasserA(StatutValidation cible) {
        return this == EN_ATTENTE && (cible == VALIDEE || cible == REFUSEE);
    }
}
