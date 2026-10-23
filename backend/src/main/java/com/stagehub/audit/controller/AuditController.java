package com.stagehub.audit.controller;

import com.stagehub.audit.dto.AuditLogResponse;
import com.stagehub.audit.entity.EntiteAuditee;
import com.stagehub.audit.service.AuditService;
import com.stagehub.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal d'audit, réservé à l'admin (préfixe /api/v1/admin, cf. SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Administration")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Journal d'audit (du plus récent au plus ancien), filtrable par type d'entité")
    public PageResponse<AuditLogResponse> journal(
            @RequestParam EntiteAuditee entite,
            @ParameterObject @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.lister(entite, pageable);
    }
}
