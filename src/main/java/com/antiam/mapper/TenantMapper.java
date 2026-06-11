package com.antiam.mapper;

import com.antiam.domain.Tenant;
import com.antiam.domain.TenantSetting;
import com.antiam.dto.TenantDtos.TenantResponse;
import com.antiam.dto.TenantDtos.TenantSettingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TenantMapper {

    /**
     * 将租户实体转换为管理接口响应。
     */
    TenantResponse toResponse(Tenant tenant);

    /**
     * 将租户配置实体转换为响应；敏感值只返回脱敏占位符。
     */
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "settingValue", expression = "java(maskSensitive(setting))")
    TenantSettingResponse toResponse(TenantSetting setting);

    default String maskSensitive(TenantSetting setting) {
        return setting.isSensitive() && setting.getSettingValue() != null ? "******" : setting.getSettingValue();
    }
}
