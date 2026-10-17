package com.stagehub.audit.service;

import com.stagehub.audit.dto.AuditLogResponse;
import com.stagehub.audit.entity.ActionAudit;
import com.stagehub.audit.entity.AuditLog;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.audit.mapper.AuditLogMapper;
import com.stagehub.audit.repository.AuditLogRepository;
import com.stagehub.common.dto.PageResponse;
import org.springframework.data.domain.Page;
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
    private final AuditLogMapper auditLogMapper;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    /** Journal paginé, filtrable par type d'entité (null = tout). */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> lister(EntiteAuditee entite, Pageable pageable) {
        Page<AuditLog> page = entite == null
                ? auditLogRepository.findAll(pageable)
                : auditLogRepository.findByEntite(entite, pageable);
        return PageResponse.of(page, auditLogMapper::toResponse);
    }

    /**
     * MANDATORY : refuse de s'exécuter sans transaction déjà ouverte. L'audit rejoint ainsi la transaction
     * de l'action auditée (rollback commun) ; si l'ordre des proxys était mal configuré, l'erreur serait
     * immédiate au lieu d'un audit enregistré à part.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enregistrer(ActionAudit action, Long entiteId, String ancienneValeur, String nouvelleValeur) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntite(action.entite());
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
