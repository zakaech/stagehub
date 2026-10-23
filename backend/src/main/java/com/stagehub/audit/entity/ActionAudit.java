package com.stagehub.audit.entity;

/**
 * Actions auditées ; chacune porte le type d'entité qu'elle modifie. Depuis la phase 7 (c), la validation des
 * entreprises est auditée par offre-service (bibliothèque audit).
 */
public enum ActionAudit {
    CHANGEMENT_STATUT_CANDIDATURE(EntiteAuditee.CANDIDATURE);

    private final EntiteAuditee entite;

    ActionAudit(EntiteAuditee entite) {
        this.entite = entite;
    }

    public EntiteAuditee entite() {
        return entite;
    }
}
