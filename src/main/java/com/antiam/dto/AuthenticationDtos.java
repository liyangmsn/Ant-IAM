package com.antiam.dto;

import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.MfaFactorType;
import com.antiam.domain.SessionRestriction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        String location,
        String deviceType,
        Instant createdAt,
        Instant lastAccessedAt,
        Instant expiresAt,
        Instant endedAt,
        boolean active,
        @Schema(description = "会话受限原因：PASSWORD_CHANGE 需先修改密码，MFA_ENROLLMENT 需先绑定 MFA；为空表示无限制")
        SessionRestriction restriction
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

    public record LogoutResponse(
        @Schema(description = "登出后应跳转的地址；仅当 service 为已注册的 CAS 应用地址时返回")
        String redirectTo
    ) {
    }

    public record SendSmsCodeRequest(
        @Schema(description = "手机号", example = "13800000000")
        @NotBlank String mobile,
        @Schema(description = "验证码用途", example = "LOGIN")
        String purpose
    ) {
    }

    public record SendSmsCodeResponse(
        String mobile,
        String purpose,
        Instant expiresAt
    ) {
    }

    public record MobileLoginRequest(
        @Schema(description = "手机号", example = "13800000000")
        @NotBlank String mobile,
        @Schema(description = "短信验证码", example = "666666")
        @NotBlank String code,
        @Schema(description = "记住我，使用记住我有效期")
        Boolean rememberMe
    ) {
    }

    public record PasswordLoginRequest(
        @Schema(description = "登录账号", example = "zhangsan")
        @NotBlank String username,
        @Schema(description = "登录密码", example = "ChangeMe123")
        @NotBlank String password,
        @Schema(description = "记住我，使用记住我有效期")
        Boolean rememberMe
    ) {
    }

    public record MobileLoginResponse(
        UUID userId,
        AuthenticationSessionResponse session,
        @Schema(description = "是否需要继续完成 MFA 二次验证；为 true 时 session 为空")
        boolean mfaRequired,
        LoginMfaChallengeResponse mfa
    ) {
    }

    public record PasswordLoginResponse(
        UUID userId,
        @Schema(description = "认证会话；需要 MFA 二次验证时为空")
        AuthenticationSessionResponse session,
        boolean temporaryPassword,
        boolean passwordExpired,
        boolean passwordChangeRequired,
        @Schema(description = "是否需要 MFA 二次验证")
        boolean mfaRequired,
        @Schema(description = "MFA 二次验证挑战")
        LoginMfaChallengeResponse mfa,
        @Schema(description = "密码即将过期时的剩余天数；不在提醒期内或已过期时为空")
        Integer passwordExpiresInDays
    ) {
    }

    public record LoginMfaFactorOption(UUID id, MfaFactorType type, String name) {
    }

    public record LoginMfaChallengeResponse(
        @Schema(description = "登录 MFA 挑战标识")
        String challengeId,
        UUID factorId,
        MfaFactorType factorType,
        @Schema(description = "验证码投递目标（已脱敏）")
        String deliveryHint,
        Instant expiresAt,
        @Schema(description = "原型环境下 WebAuthn 因子返回的挑战码")
        String code,
        @Schema(description = "可切换的 MFA 因子")
        List<LoginMfaFactorOption> factors,
        @Schema(description = "是否允许使用恢复码")
        boolean recoveryCodeAllowed
    ) {
    }

    public record MfaLoginRequest(
        @Schema(description = "登录 MFA 挑战标识")
        @NotBlank String challengeId,
        @Schema(description = "验证码、TOTP 动态码或恢复码")
        @NotBlank String code,
        @Schema(description = "使用恢复码校验")
        Boolean recoveryCode,
        @Schema(description = "记住我，使用记住我有效期")
        Boolean rememberMe
    ) {
    }

    public record SwitchMfaFactorRequest(
        @Schema(description = "登录 MFA 挑战标识")
        @NotBlank String challengeId,
        @Schema(description = "切换到的 MFA 因子 UUID")
        @NotNull UUID factorId
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

    public record ThirdPartyAuthorizeResponse(
        String providerKey,
        String authorizationUrl,
        String state
    ) {
    }

    public record ThirdPartyLoginCallbackRequest(
        @Schema(description = "第三方授权码")
        @NotBlank String code,
        @Schema(description = "回调 state")
        String state,
        @Schema(description = "本次授权使用的回调地址；为空则使用认证源配置")
        String redirectUri
    ) {
    }

    public record ThirdPartyIdentityResponse(
        String providerKey,
        String provider,
        String subject,
        String unionId,
        String displayName,
        String email,
        String mobile,
        String avatarUrl,
        Map<String, Object> raw
    ) {
    }

    public record ThirdPartyLoginResponse(
        ThirdPartyIdentityResponse identity,
        AuthenticationSessionResponse session,
        UUID userId,
        boolean userCreated,
        @Schema(description = "是否需要继续完成 MFA 二次验证；为 true 时 session 为空")
        boolean mfaRequired,
        LoginMfaChallengeResponse mfa
    ) {
    }

    public record ThirdPartyBindingResponse(
        UUID id,
        UUID userId,
        String providerKey,
        String provider,
        String subject,
        String unionId,
        String displayName,
        String email,
        String mobile,
        String avatarUrl,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
