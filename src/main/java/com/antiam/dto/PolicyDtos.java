package com.antiam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.antiam.domain.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public final class PolicyDtos {
    private PolicyDtos() {
    }

    public record CreateAuthenticationPolicyRequest(
        @Schema(description = "认证策略编码，系统内唯一", example = "default_login_policy")
        @NotBlank String code,
        @Schema(description = "认证策略名称", example = "默认登录策略")
        @NotBlank String name,
        @Schema(description = "策略优先级，数值越小优先级越高", example = "100")
        int priority,
        @Schema(description = "是否要求 MFA")
        boolean mfaRequired,
        @Schema(description = "是否要求未注册 MFA 的用户先完成 MFA 注册")
        boolean mfaEnrollmentRequired,
        @Schema(description = "达到该风险等级时触发增强认证")
        RiskLevel stepUpRiskLevel,
        @Schema(description = "达到该风险等级时拒绝认证")
        RiskLevel denyRiskLevel,
        @Schema(description = "密码最小长度", example = "8")
        @Min(8) Integer passwordMinLength,
        @Schema(description = "密码连续失败锁定阈值", example = "5")
        @Min(1) Integer passwordMaxFailureAttempts,
        @Schema(description = "密码过期天数，0 表示不过期", example = "90")
        @Min(0) Integer passwordExpiresInDays,
        @Schema(description = "密码历史记忆数量，用于防止重复使用旧密码", example = "3")
        @Min(0) Integer passwordHistoryCount
    ) {
    }

    public record UpdateAuthenticationPolicyRequest(
        @Schema(description = "认证策略名称", example = "默认登录策略")
        @NotBlank String name,
        @Schema(description = "策略优先级，数值越小优先级越高", example = "100")
        int priority,
        @Schema(description = "是否要求 MFA")
        boolean mfaRequired,
        @Schema(description = "是否要求未注册 MFA 的用户先完成 MFA 注册")
        boolean mfaEnrollmentRequired,
        @Schema(description = "达到该风险等级时触发增强认证")
        RiskLevel stepUpRiskLevel,
        @Schema(description = "达到该风险等级时拒绝认证")
        RiskLevel denyRiskLevel,
        @Schema(description = "密码最小长度", example = "8")
        @Min(8) Integer passwordMinLength,
        @Schema(description = "密码连续失败锁定阈值", example = "5")
        @Min(1) Integer passwordMaxFailureAttempts,
        @Schema(description = "密码过期天数，0 表示不过期", example = "90")
        @Min(0) Integer passwordExpiresInDays,
        @Schema(description = "密码历史记忆数量，用于防止重复使用旧密码", example = "3")
        @Min(0) Integer passwordHistoryCount
    ) {
    }

    public record AuthenticationPolicyResponse(
        UUID id,
        String code,
        String name,
        int priority,
        boolean mfaRequired,
        boolean mfaEnrollmentRequired,
        RiskLevel stepUpRiskLevel,
        RiskLevel denyRiskLevel,
        int passwordMinLength,
        int passwordMaxFailureAttempts,
        int passwordExpiresInDays,
        int passwordHistoryCount,
        boolean enabled
    ) {
    }

    public record EvaluateAuthenticationPolicyRequest(
        @Schema(description = "待评估用户 UUID")
        @NotNull UUID userId,
        @Schema(description = "客户端 IP 地址", example = "192.168.1.10")
        String ipAddress,
        @Schema(description = "User-Agent")
        String userAgent,
        @Schema(description = "设备指纹")
        String deviceFingerprint,
        @Schema(description = "地理位置标识", example = "CN")
        String geoLocation
    ) {
    }

    public record AuthenticationPolicyDecisionResponse(
        UUID policyId,
        String policyCode,
        boolean mfaRequired,
        boolean mfaEnrollmentRequired,
        boolean userHasMfaFactor,
        int passwordMinLength,
        int passwordMaxFailureAttempts,
        int passwordExpiresInDays,
        int passwordHistoryCount,
        RiskLevel stepUpRiskLevel,
        RiskLevel denyRiskLevel,
        RiskLevel riskLevel,
        String decision
    ) {
    }
}
