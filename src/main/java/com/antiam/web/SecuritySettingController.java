package com.antiam.web;

import static com.antiam.dto.SecuritySettingDtos.GeneralSecuritySettingsResponse;
import static com.antiam.dto.SecuritySettingDtos.PasswordPolicySettingsResponse;
import static com.antiam.dto.SecuritySettingDtos.UpdateGeneralSecuritySettingsRequest;
import static com.antiam.dto.SecuritySettingDtos.UpdatePasswordPolicySettingsRequest;

import com.antiam.service.SecuritySettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security-settings")
@RequiredArgsConstructor
@Tag(name = "安全设置", description = "控制台通用安全和密码策略页面设置接口")
public class SecuritySettingController {

    private final SecuritySettingService securitySettings;

    /**
     * 查询通用安全设置。
     */
    @Operation(summary = "查询通用安全设置", description = "返回用户并发、会话、验证码、登录失败锁定和 CSP 等通用安全配置。")
    @GetMapping("/general")
    GeneralSecuritySettingsResponse general() {
        return securitySettings.general();
    }

    /**
     * 保存通用安全设置。
     */
    @Operation(summary = "保存通用安全设置", description = "保存用户并发、会话、验证码、登录失败锁定和 CSP 等通用安全配置。")
    @PutMapping("/general")
    GeneralSecuritySettingsResponse updateGeneral(
        @Parameter(description = "通用安全设置保存请求") @Valid @RequestBody UpdateGeneralSecuritySettingsRequest request,
        Principal principal
    ) {
        return securitySettings.updateGeneral(request, principal.getName());
    }

    /**
     * 查询密码策略设置。
     */
    @Operation(summary = "查询密码策略设置", description = "返回密码长度、复杂度、过期、历史密码和弱密码等策略配置。")
    @GetMapping("/password-policy")
    PasswordPolicySettingsResponse passwordPolicy() {
        return securitySettings.passwordPolicy();
    }

    /**
     * 保存密码策略设置。
     */
    @Operation(summary = "保存密码策略设置", description = "保存密码长度、复杂度、过期、历史密码和弱密码等策略配置。")
    @PutMapping("/password-policy")
    PasswordPolicySettingsResponse updatePasswordPolicy(
        @Parameter(description = "密码策略设置保存请求") @Valid @RequestBody UpdatePasswordPolicySettingsRequest request,
        Principal principal
    ) {
        return securitySettings.updatePasswordPolicy(request, principal.getName());
    }
}
