package com.antiam.dto;

import com.antiam.domain.SettingValueType;
import com.antiam.domain.TenantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class TenantDtos {
    private TenantDtos() {
    }

    public record CreateTenantRequest(
        @Schema(description = "租户编码，系统内唯一", example = "acme")
        @NotBlank String code,
        @Schema(description = "租户名称", example = "Acme 公司")
        @NotBlank String name,
        @Schema(description = "租户域名", example = "acme.example.com")
        @NotBlank String domain
    ) {
    }

    public record UpdateTenantRequest(
        @Schema(description = "租户名称", example = "Acme 公司")
        @NotBlank String name,
        @Schema(description = "租户域名", example = "acme.example.com")
        @NotBlank String domain
    ) {
    }

    public record TenantResponse(UUID id, String code, String name, String domain, TenantStatus status) {
    }

    public record UpsertTenantSettingRequest(
        @Schema(description = "配置键", example = "login.session.timeout")
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

    public record TenantSettingResponse(
        UUID id,
        UUID tenantId,
        String settingKey,
        String category,
        SettingValueType valueType,
        String settingValue,
        String description,
        boolean sensitive
    ) {
    }
}
