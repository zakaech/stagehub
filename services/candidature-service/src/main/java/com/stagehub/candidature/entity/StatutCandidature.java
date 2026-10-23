package com.stagehub.candidature.entity;

/**
 * Machine à états d'une candidature (spec §4.2) :
 * EN_ATTENTE → ENTRETIEN | REFUSEE ; ENTRETIEN → ACCEPTEE | REFUSEE ; ACCEPTEE et REFUSEE sont finaux.
 */
public enum StatutCandidature {
    EN_ATTENTE,
    ENTRETIEN,
    ACCEPTEE,
    REFUSEE;

    public boolean peutPasserA(StatutCandidature cible) {
        return switch (this) {
            case EN_ATTENTE -> cible == ENTRETIEN || cible == REFUSEE;
            case ENTRETIEN -> cible == ACCEPTEE || cible == REFUSEE;
            case ACCEPTEE, REFUSEE -> false;
        };
    }

    public boolean estFinal() {
        return this == ACCEPTEE || this == REFUSEE;
    }
}
