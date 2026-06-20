package com.antiam.dto;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.MfaChallengeStatus;
import com.antiam.domain.MfaFactorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

public final class UserDtos {
    private UserDtos() {
    }

    public record CreateUserRequest(
        @Schema(description = "登录账号，租户内唯一", example = "zhangsan")
        @NotBlank String username,
        @Schema(description = "用户显示名称", example = "张三")
        @NotBlank String displayName,
        @Schema(description = "邮箱地址", example = "zhangsan@example.com")
        @Email String email,
        @Schema(description = "手机号", example = "13800000000")
        String mobile,
        @Schema(description = "所属租户 UUID")
        UUID tenantId,
        @Schema(description = "所属组织 UUID")
        UUID organizationId,
        @Schema(description = "初始密码，至少 8 位；为空时不设置初始密码", example = "ChangeMe123")
        @Size(min = 8) String initialPassword
    ) {
    }

    public record SetPasswordRequest(
        @Schema(description = "新密码，至少 8 位", example = "ChangeMe123")
        @NotBlank @Size(min = 8) String password,
        @Schema(description = "是否为临时密码；临时密码要求用户下次登录后修改")
        boolean temporary
    ) {
    }

    public record ChangeOwnPasswordRequest(
        @Schema(description = "当前密码", example = "OldPass123")
        @NotBlank String currentPassword,
        @Schema(description = "新密码，至少 8 位", example = "NewPass123")
        @NotBlank @Size(min = 8) String newPassword
    ) {
    }

    public record SendMobileBindingCodeRequest(
        @Schema(description = "手机号", example = "13800000000")
        @NotBlank String mobile
    ) {
    }

    public record BindMobileRequest(
        @Schema(description = "手机号", example = "13800000000")
        @NotBlank String mobile,
        @Schema(description = "短信验证码", example = "666666")
        @NotBlank String code
    ) {
    }

    public record CreatePasswordResetTicketRequest(
        @Schema(description = "需要重置密码的用户 UUID")
        @NotNull UUID userId,
        @Schema(description = "票据有效分钟数；为空时使用系统默认值", example = "30")
        Integer expiresInMinutes
    ) {
    }

    public record PasswordResetTicketResponse(
        UUID id,
        UUID userId,
        String resetToken,
        Instant expiresAt,
        boolean consumed
    ) {
    }

    public record PasswordResetTicketDetailResponse(
        UUID id,
        UUID userId,
        String username,
        Instant createdAt,
        Instant expiresAt,
        Instant consumedAt,
        String requestedBy,
        boolean expired,
        boolean usable
    ) {
    }

    public record ConsumePasswordResetTicketRequest(
        @Schema(description = "密码重置 token，只在创建票据时返回一次")
        @NotBlank String resetToken,
        @Schema(description = "新密码，至少 8 位", example = "NewPass123")
        @NotBlank @Size(min = 8) String newPassword,
        @Schema(description = "是否设置为临时密码")
        boolean temporary
    ) {
    }

    public record UpdateUserRequest(
        @Schema(description = "用户显示名称", example = "张三")
        @NotBlank String displayName,
        @Schema(description = "邮箱地址", example = "zhangsan@example.com")
        @Email String email,
        @Schema(description = "手机号", example = "13800000000")
        String mobile,
        @Schema(description = "所属组织 UUID；为空表示不绑定组织")
        UUID organizationId
    ) {
    }

    public record VerifyPasswordRequest(
        @Schema(description = "登录账号", example = "zhangsan")
        @NotBlank String username,
        @Schema(description = "待校验密码", example = "ChangeMe123")
        @NotBlank String password
    ) {
    }

    public record VerifyPasswordResponse(
        boolean valid,
        boolean temporaryPassword,
        int failedAttempts,
        boolean locked,
        boolean passwordExpired,
        boolean passwordChangeRequired
    ) {
    }

    public record RegisterMfaFactorRequest(
        @Schema(description = "MFA 因子类型")
        @NotNull MfaFactorType type,
        @Schema(description = "MFA 因子名称", example = "手机验证")
        @NotBlank String name,
        @Schema(description = "MFA 密钥；TOTP 为空时由服务端生成")
        String secret
    ) {
    }

    public record UpdateMfaFactorRequest(
        @Schema(description = "MFA 因子名称", example = "主力手机")
        @NotBlank String name
    ) {
    }

    public record RecoveryCodesResponse(UUID factorId, List<String> recoveryCodes) {
    }

    public record MfaFactorResponse(
        UUID id,
        MfaFactorType type,
        String name,
        boolean verified,
        boolean enabled,
        String secret,
        String provisioningUri
    ) {
    }

    public record StartMfaChallengeRequest(
        @Schema(description = "MFA 因子 UUID")
        @NotNull UUID factorId
    ) {
    }

    public record MfaChallengeResponse(
        String challengeId,
        UUID factorId,
        MfaFactorType type,
        MfaChallengeStatus status,
        String deliveryHint,
        String verificationCode
    ) {
    }

    public record MfaChallengeDetailResponse(
        UUID id,
        String challengeId,
        UUID userId,
        String username,
        UUID factorId,
        MfaFactorType type,
        MfaChallengeStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant verifiedAt,
        int attempts
    ) {
    }

    public record VerifyMfaChallengeRequest(
        @Schema(description = "MFA 挑战 ID")
        @NotBlank String challengeId,
        @Schema(description = "用户输入的验证码或恢复码", example = "123456")
        @NotBlank String code
    ) {
    }

    public record VerifyMfaChallengeResponse(boolean valid, MfaChallengeStatus status) {
    }

    public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String email,
        String mobile,
        AccountStatus status,
        UUID tenantId,
        UUID organizationId,
        Set<String> groups,
        Set<String> roles
    ) {
    }

    public record EffectivePermissionResponse(
        String code,
        String name,
        String description,
        Set<String> roles,
        Set<String> sources
    ) {
    }

    public record UserEffectiveAccessResponse(
        UUID userId,
        String username,
        AccountStatus status,
        Set<String> directRoles,
        Set<String> groupRoles,
        List<EffectivePermissionResponse> permissions
    ) {
    }
}
