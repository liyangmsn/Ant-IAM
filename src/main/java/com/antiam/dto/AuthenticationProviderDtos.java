package com.antiam.dto;

import com.antiam.domain.AuthenticationProviderKind;
import com.antiam.domain.AuthenticationProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class AuthenticationProviderDtos {
    private AuthenticationProviderDtos() {
    }

    public record CreateAuthenticationProviderRequest(
        @Schema(description = "认证源唯一编码", example = "github")
        @NotBlank String providerKey,
        @Schema(description = "认证源名称", example = "GitHub认证")
        @NotBlank String name,
        @Schema(description = "认证源提供商")
        @NotNull AuthenticationProviderKind provider,
        @Schema(description = "认证源类型")
        @NotNull AuthenticationProviderType type,
        @Schema(description = "认证源说明")
        String description,
        @Schema(description = "认证源 JSON 配置，保存 AppId/AppSecret 等参数摘要")
        String configuration,
        @Schema(description = "是否在登录页展示")
        boolean visible,
        @Schema(description = "是否启用")
        boolean enabled
    ) {
    }

    public record UpdateAuthenticationProviderRequest(
        @Schema(description = "认证源名称", example = "GitHub认证")
        @NotBlank String name,
        @Schema(description = "认证源提供商")
        @NotNull AuthenticationProviderKind provider,
        @Schema(description = "认证源类型")
        @NotNull AuthenticationProviderType type,
        @Schema(description = "认证源说明")
        String description,
        @Schema(description = "认证源 JSON 配置，保存 AppId/AppSecret 等参数摘要")
        String configuration,
        @Schema(description = "是否在登录页展示")
        boolean visible
    ) {
    }

    public record AuthenticationProviderResponse(
        UUID id,
        String providerKey,
        String name,
        AuthenticationProviderKind provider,
        AuthenticationProviderType type,
        String description,
        String configuration,
        boolean visible,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
