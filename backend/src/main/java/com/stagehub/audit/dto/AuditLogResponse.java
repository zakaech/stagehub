package com.stagehub.audit.dto;

import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.audit.entity.EntiteAuditee;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        ActionAudit action,
        String utilisateur,
        EntiteAuditee entite,
        Long entiteId,
        String ancienneValeur,
        String nouvelleValeur,
        Instant date
) {
}
