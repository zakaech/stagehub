package com.stagehub.audit.mapper;

import com.stagehub.audit.dto.AuditLogResponse;
import com.stagehub.audit.entity.AuditLog;
import com.stagehub.common.config.MapStructConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
