package com.stagehub.audit.dto;

import com.stagehub.audit.entity.AuditLog;

import java.time.Instant;

/** Même format JSON que le journal du monolithe : la page Angular ne change pas. */
public record AuditLogResponse(
        Long id,
        String action,
        String utilisateur,
        String entite,
        Long entiteId,
        String ancienneValeur,
        String nouvelleValeur,
        Instant date
) {

    public static AuditLogResponse de(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getUtilisateur(), log.getEntite(),
                log.getEntiteId(), log.getAncienneValeur(), log.getNouvelleValeur(), log.getDate());
    }
}
