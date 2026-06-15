package com.antiam.dto;

import com.antiam.domain.IdentitySourceType;
import com.antiam.domain.IdentitySyncMode;
import com.antiam.domain.IdentitySyncRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class IdentitySourceDtos {
    private IdentitySourceDtos() {
    }

    public record CreateIdentitySourceRequest(
        @Schema(description = "身份源编码，系统内唯一", example = "ldap_main")
        @NotBlank String code,
        @Schema(description = "身份源名称", example = "公司 LDAP")
        @NotBlank String name,
        @Schema(description = "身份源类型")
        @NotNull IdentitySourceType type,
        @Schema(description = "所属租户 UUID；为空表示系统级身份源")
        UUID tenantId
    ) {
    }

    public record IdentitySourceResponse(
        UUID id,
        String code,
        String name,
        IdentitySourceType type,
        boolean enabled,
        UUID tenantId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record UpdateIdentitySourceRequest(
        @Schema(description = "身份源名称", example = "公司 LDAP")
        @NotBlank String name
    ) {
    }

    public record ConfigureConnectorRequest(
        @Schema(description = "连接器 JSON 配置，例如服务地址、同步映射和模拟同步数据")
        String configuration,
        @Schema(description = "密钥引用，不直接保存明文凭据")
        String secretRef
    ) {
    }

    public record ConnectorResponse(UUID id, UUID identitySourceId, String configuration, String secretRef, boolean enabled) {
    }

    public record CreateSyncJobRequest(
        @Schema(description = "同步任务名称", example = "每日全量同步")
        @NotBlank String name,
        @Schema(description = "同步模式")
        @NotNull IdentitySyncMode mode,
        @Schema(description = "Cron 表达式；为空表示仅手动触发", example = "0 0 2 * * ?")
        String cronExpression
    ) {
    }

    public record UpdateSyncJobRequest(
        @Schema(description = "同步任务名称", example = "每日全量同步")
        @NotBlank String name,
        @Schema(description = "同步模式")
        @NotNull IdentitySyncMode mode,
        @Schema(description = "Cron 表达式；为空表示仅手动触发", example = "0 0 2 * * ?")
        String cronExpression
    ) {
    }

    public record SyncJobResponse(UUID id, UUID identitySourceId, String name, IdentitySyncMode mode, String cronExpression, boolean enabled) {
    }

    public record SyncRunResponse(
        UUID id,
        UUID syncJobId,
        IdentitySyncRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        int usersCreated,
        int usersUpdated,
        int groupsCreated,
        int groupsUpdated,
        String message
    ) {
    }
}
