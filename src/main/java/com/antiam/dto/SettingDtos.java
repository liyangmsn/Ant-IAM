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

    @Schema(description = "集成测试结果")
    public record IntegrationTestResponse(
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "结果说明") String message
    ) {
    }

    @Schema(description = "测试邮件请求")
    public record MailTestRequest(
        @Schema(description = "测试收件人邮箱") @NotBlank String to,
        @Schema(description = "邮件模板键", example = "login_verify") String templateKey
    ) {
    }

    @Schema(description = "测试短信请求")
    public record SmsTestRequest(
        @Schema(description = "测试手机号") @NotBlank String mobile,
        @Schema(description = "发送场景模板类型", example = "登录验证") String templateType
    ) {
    }

    @Schema(description = "GeoIP 数据库更新请求")
    public record GeoIpUpdateRequest(
        @Schema(description = "MaxMind License Key") String licenseKey,
        @Schema(description = "数据库保存路径") String databasePath
    ) {
    }
}
