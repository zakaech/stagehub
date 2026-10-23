package com.stagehub.audit.controller;

import com.stagehub.audit.aspect.LecteurEtatAudit;
import com.stagehub.audit.dto.AuditLogResponse;
import com.stagehub.audit.service.AuditService;
import com.stagehub.commons.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Journal d'audit du service, réservé à l'admin (préfixe /api/v1/admin, cf. la sécurité du service).
 * Le paramètre entite est obligatoire : la gateway envoie chaque type d'entité au service qui le détient.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Administration")
public class AuditController {

    private final AuditService auditService;
    private final List<String> entitesAuditees;

    public AuditController(AuditService auditService, List<LecteurEtatAudit> lecteurs) {
        this.auditService = auditService;
        this.entitesAuditees = lecteurs.stream().map(LecteurEtatAudit::entite).toList();
    }

    @GetMapping
    @Operation(summary = "Journal d'audit d'un type d'entité (du plus récent au plus ancien)")
    public PageResponse<AuditLogResponse> journal(
            @RequestParam String entite,
            @ParameterObject @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        if (!entitesAuditees.contains(entite)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Entité inconnue de ce service : " + entite + " (attendu : " + String.join(", ", entitesAuditees) + ")");
        }
        return auditService.lister(entite, pageable);
    }
}
