package com.antiam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public final class SecuritySettingDtos {
    private SecuritySettingDtos() {
    }

    public record GeneralSecuritySettingsResponse(
        int userConcurrentSessions,
        int sessionTtlSeconds,
        int rememberMeTtlSeconds,
        int captchaTtlMinutes,
        int loginFailureWindowMinutes,
        int loginFailureMaxAttempts,
        int autoUnlockMinutes,
        String contentSecurityPolicy
    ) {
    }

    public record UpdateGeneralSecuritySettingsRequest(
        @Schema(description = "同一用户允许同时在线的会话数，-1 表示不限制")
        @Min(-1) int userConcurrentSessions,
        @Schema(description = "会话有效期，单位秒")
        @Min(1) int sessionTtlSeconds,
        @Schema(description = "记住我有效期，单位秒")
        @Min(1) int rememberMeTtlSeconds,
        @Schema(description = "验证码有效期，单位分钟")
        @Min(1) int captchaTtlMinutes,
        @Schema(description = "连续登录失败统计窗口，单位分钟")
        @Min(1) int loginFailureWindowMinutes,
        @Schema(description = "连续登录失败锁定阈值")
        @Min(1) int loginFailureMaxAttempts,
        @Schema(description = "自动解锁时间，单位分钟")
        @Min(1) int autoUnlockMinutes,
        @Schema(description = "内容安全策略 CSP")
        @NotBlank String contentSecurityPolicy
    ) {
    }

    public record PasswordPolicySettingsResponse(
        int minLength,
        int maxLength,
        String complexity,
        int passwordExpiresInDays,
        int expiryReminderDays,
        int maxRepeatedChars,
        boolean checkUserInfo,
        boolean historyCheckEnabled,
        int passwordHistoryCount,
        boolean illegalSequenceCheckEnabled,
        boolean weakPasswordCheckEnabled,
        String additionalWeakPasswords,
        Set<String> extensionRules
    ) {
    }

    public record UpdatePasswordPolicySettingsRequest(
        @Min(1) int minLength,
        @Min(1) int maxLength,
        @NotBlank String complexity,
        @Min(0) int passwordExpiresInDays,
        @Min(0) int expiryReminderDays,
        @Min(0) int maxRepeatedChars,
        boolean checkUserInfo,
        boolean historyCheckEnabled,
        @Min(0) int passwordHistoryCount,
        boolean illegalSequenceCheckEnabled,
        boolean weakPasswordCheckEnabled,
        String additionalWeakPasswords,
        Set<String> extensionRules
    ) {
    }
}
