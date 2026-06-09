package com.antiam.dto;

import com.antiam.domain.SettingValueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class SettingDtos {
    private SettingDtos() {
    }

    public record UpsertSettingRequest(
        @Schema(description = "配置键", example = "password.min.length")
        @NotBlank String settingKey,
        @Schema(description = "配置分类", example = "security")
        @NotBlank String category,
        @Schema(description = "配置值类型")
        @NotNull SettingValueType valueType,
        @Schema(description = "配置值；敏感配置响应时会脱敏")
        String settingValue,
        @Schema(description = "配置说明")
        String description,
        @Schema(description = "是否敏感配置")
        boolean sensitive
    ) {
    }

    public record SettingResponse(
        UUID id,
        String settingKey,
        String category,
        SettingValueType valueType,
        String settingValue,
        String description,
        boolean sensitive
    ) {
    }
}
