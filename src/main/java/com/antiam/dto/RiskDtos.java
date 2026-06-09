package com.antiam.dto;

import com.antiam.domain.RiskLevel;
import com.antiam.domain.RiskRuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RiskDtos {
    private RiskDtos() {
    }

    public record CreateRiskRuleRequest(
        @Schema(description = "风险规则编码，系统内唯一", example = "geo_not_allowed")
        @NotBlank String code,
        @Schema(description = "风险规则名称", example = "非常用地区登录")
        @NotBlank String name,
        @Schema(description = "风险规则类型")
        @NotNull RiskRuleType type,
        @Schema(description = "规则匹配值，例如 IP 片段、User-Agent 关键字或允许地区列表")
        String conditionValue,
        @Schema(description = "阈值，例如失败登录次数阈值", example = "5")
        int threshold,
        @Schema(description = "命中规则后的风险等级")
        @NotNull RiskLevel riskLevel
    ) {
    }

    public record UpdateRiskRuleRequest(
        @Schema(description = "风险规则名称", example = "非常用地区登录")
        @NotBlank String name,
        @Schema(description = "风险规则类型")
        @NotNull RiskRuleType type,
        @Schema(description = "规则匹配值，例如 IP 片段、User-Agent 关键字或允许地区列表")
        String conditionValue,
        @Schema(description = "阈值，例如失败登录次数阈值", example = "5")
        int threshold,
        @Schema(description = "命中规则后的风险等级")
        @NotNull RiskLevel riskLevel
    ) {
    }

    public record RiskRuleResponse(
        UUID id,
        String code,
        String name,
        RiskRuleType type,
        String conditionValue,
        int threshold,
        RiskLevel riskLevel,
        boolean enabled
    ) {
    }

    public record EvaluateRiskRequest(
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

    public record RiskAssessmentResponse(
        UUID id,
        UUID userId,
        Instant createdAt,
        String ipAddress,
        String userAgent,
        String deviceFingerprint,
        String geoLocation,
        RiskLevel riskLevel,
        List<String> matchedRules,
        String decision
    ) {
    }
}
