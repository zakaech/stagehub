package com.stagehub.audit.repository;

import com.stagehub.audit.entity.AuditLog;
import com.stagehub.audit.entity.EntiteAuditee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntite(EntiteAuditee entite, Pageable pageable);

    List<AuditLog> findByEntiteAndEntiteIdOrderByIdAsc(EntiteAuditee entite, Long entiteId);
}
