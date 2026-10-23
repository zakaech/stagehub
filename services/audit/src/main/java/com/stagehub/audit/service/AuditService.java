package com.stagehub.audit.service;

import com.stagehub.audit.dto.AuditLogResponse;
import com.stagehub.audit.entity.AuditLog;
import com.stagehub.audit.repository.AuditLogRepository;
import com.stagehub.commons.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuditService {

    static final String SYSTEME = "systeme";

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    /** Journal paginé d'un type d'entité (filtre obligatoire depuis la phase 7 : chaque service a son journal). */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> lister(String entite, Pageable pageable) {
        return PageResponse.of(auditLogRepository.findByEntite(entite, pageable), AuditLogResponse::de);
    }

    /**
     * MANDATORY : refuse de s'exécuter sans transaction déjà ouverte. L'audit rejoint ainsi la transaction de l'action
     * auditée (rollback commun) ; si l'ordre des proxys était mal configuré, l'erreur serait immédiate.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(String action, String entite, Long entiteId, String ancienneValeur, String nouvelleValeur) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntite(entite);
        log.setEntiteId(entiteId);
        log.setAncienneValeur(ancienneValeur);
        log.setNouvelleValeur(nouvelleValeur);
        log.setUtilisateur(utilisateurCourant());
        log.setDate(clock.instant());
        auditLogRepository.save(log);
    }

    /** Lu dans le SecurityContext du thread courant (le thread de la requête HTTP, l'aspect étant synchrone). */
    private static String utilisateurCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        return authentification != null && authentification.isAuthenticated() ? authentification.getName() : SYSTEME;
    }
}
