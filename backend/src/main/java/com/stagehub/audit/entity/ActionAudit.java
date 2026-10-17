package com.stagehub.audit.entity;

/** Actions auditées ; chacune porte le type d'entité qu'elle modifie. */
public enum ActionAudit {
    CHANGEMENT_STATUT_CANDIDATURE(EntiteAuditee.CANDIDATURE),
    VALIDATION_ENTREPRISE(EntiteAuditee.ENTREPRISE);

    private final EntiteAuditee entite;

    ActionAudit(EntiteAuditee entite) {
        this.entite = entite;
    }

    public EntiteAuditee entite() {
        return entite;
    }
}
