package com.antiam.mapper;

import com.antiam.domain.SystemSetting;
import com.antiam.dto.SettingDtos.SettingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SettingMapper {

    /**
     * 将系统配置实体转换为响应；敏感值只返回脱敏占位符。
     */
    @Mapping(target = "settingValue", expression = "java(maskSensitive(setting))")
    SettingResponse toResponse(SystemSetting setting);

    default String maskSensitive(SystemSetting setting) {
        return setting.isSensitive() && setting.getSettingValue() != null ? "******" : setting.getSettingValue();
    }
}
