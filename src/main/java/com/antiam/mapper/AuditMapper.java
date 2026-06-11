package com.antiam.mapper;

import com.antiam.domain.AuditEvent;
import com.antiam.dto.AuditDtos.AuditEventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditMapper {

    /**
     * 将审计事件实体转换为审计查询响应。
     */
    AuditEventResponse toResponse(AuditEvent event);
}
