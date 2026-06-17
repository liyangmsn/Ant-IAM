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
        if (!setting.isSensitive() || setting.getSettingValue() == null) {
            return setting.getSettingValue();
        }
        if (setting.getValueType() != null && setting.getValueType().name().equals("JSON")) {
            return setting.getSettingValue()
                .replaceAll("(?i)(\"(?:password|secret|secretKey|secretAccessKey|accessKey|accessKeyId|accessKeySecret|token|licenseKey)\"\\s*:\\s*\")([^\"]+)(\")", "$1******$3");
        }
        return "******";
    }
}
