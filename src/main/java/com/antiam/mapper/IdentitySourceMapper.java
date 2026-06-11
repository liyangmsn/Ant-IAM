package com.antiam.mapper;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySyncJob;
import com.antiam.domain.IdentitySyncRun;
import com.antiam.dto.IdentitySourceDtos.ConnectorResponse;
import com.antiam.dto.IdentitySourceDtos.IdentitySourceResponse;
import com.antiam.dto.IdentitySourceDtos.SyncJobResponse;
import com.antiam.dto.IdentitySourceDtos.SyncRunResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface IdentitySourceMapper {

    /**
     * 将身份源实体转换为管理端响应。
     */
    @Mapping(target = "tenantId", source = "tenant.id")
    IdentitySourceResponse toResponse(IdentitySource source);

    /**
     * 将身份源连接器实体转换为连接器配置响应。
     */
    @Mapping(target = "identitySourceId", source = "identitySource.id")
    ConnectorResponse toResponse(IdentitySourceConnector connector);

    /**
     * 将身份同步任务实体转换为任务响应。
     */
    @Mapping(target = "identitySourceId", source = "identitySource.id")
    SyncJobResponse toResponse(IdentitySyncJob job);

    /**
     * 将身份同步运行实体转换为运行记录响应。
     */
    @Mapping(target = "syncJobId", source = "syncJob.id")
    SyncRunResponse toResponse(IdentitySyncRun run);
}
