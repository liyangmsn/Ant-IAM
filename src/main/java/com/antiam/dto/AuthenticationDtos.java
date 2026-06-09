package com.antiam.dto;

import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.AuthenticationEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class AuthenticationDtos {
    private AuthenticationDtos() {
    }

    public record CreateAuthenticationSessionRequest(
        @Schema(description = "用户 UUID；为空表示未绑定用户的协议会话")
        UUID userId,
        @Schema(description = "应用 UUID；为空表示非应用会话")
        UUID applicationId,
        @Schema(description = "认证协议")
        @NotNull ApplicationProtocol protocol,
        @Schema(description = "会话索引或协议会话标识")
        @NotBlank String sessionIndex,
        @Schema(description = "客户端 IP 地址", example = "192.168.1.10")
        String ipAddress,
        @Schema(description = "User-Agent")
        String userAgent,
        @Schema(description = "会话过期时间，ISO-8601 格式")
        Instant expiresAt
    ) {
    }

    public record AuthenticationSessionResponse(
        UUID id,
        UUID userId,
        UUID applicationId,
        ApplicationProtocol protocol,
        String sessionIndex,
        String ipAddress,
        String userAgent,
        Instant expiresAt,
        Instant endedAt,
        boolean active
    ) {
    }

    public record EndAuthenticationSessionsRequest(
        @Schema(description = "用户 UUID；用于结束指定用户会话")
        UUID userId,
        @Schema(description = "应用 UUID；用于结束指定应用会话")
        UUID applicationId,
        @Schema(description = "是否结束全部活跃会话；为 false 时必须提供 userId 或 applicationId")
        Boolean all,
        @Schema(description = "结束会话原因")
        String detail
    ) {
    }

    public record EndAuthenticationSessionsResponse(
        long endedCount
    ) {
    }

    public record CreateAuthenticationEventRequest(
        @Schema(description = "认证会话 UUID")
        UUID sessionId,
        @Schema(description = "用户 UUID")
        UUID userId,
        @Schema(description = "应用 UUID")
        UUID applicationId,
        @Schema(description = "认证事件类型")
        @NotNull AuthenticationEventType type,
        @Schema(description = "客户端 IP 地址", example = "192.168.1.10")
        String ipAddress,
        @Schema(description = "User-Agent")
        String userAgent,
        @Schema(description = "事件详情")
        String detail
    ) {
    }

    public record AuthenticationEventResponse(
        UUID id,
        UUID sessionId,
        UUID userId,
        UUID applicationId,
        AuthenticationEventType type,
        String ipAddress,
        String userAgent,
        String detail,
        Instant createdAt
    ) {
    }
}
