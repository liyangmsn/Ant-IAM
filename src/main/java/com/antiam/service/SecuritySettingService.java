package com.antiam.service;

import static com.antiam.dto.SecuritySettingDtos.GeneralSecuritySettingsResponse;
import static com.antiam.dto.SecuritySettingDtos.PasswordPolicySettingsResponse;
import static com.antiam.dto.SecuritySettingDtos.UpdateGeneralSecuritySettingsRequest;
import static com.antiam.dto.SecuritySettingDtos.UpdatePasswordPolicySettingsRequest;

import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecuritySettingService {

    private static final String GENERAL_CATEGORY = "security.general";
    private static final String PASSWORD_CATEGORY = "security.password";
    private static final String DEFAULT_CSP = "default-src 'self' data:; frame-src 'self' login.dingtalk.com open.weixin.qq.com open.work.weixin.qq.com passport.feishu.cn data:; frame-ancestors 'self' eiam.topiam.cn data:; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com sf3-cn.feishucdn.com; style-src 'self' https://fonts.googleapis.com https://cdn.jsdelivr.net 'unsafe-inline'; img-src 'self' https://img.alicdn.com https://static-legacy.dingtalk.com https://api.multiavatar.com blob: data: http://www.xix.top:9999; font-src 'self' https://fonts.gstatic.com data:; worker-src 'self' https://storage.googleapis.com blob:";

    private final SystemSettingRepository settings;
    private final AuthenticationPolicyService authenticationPolicies;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    // 查询控制台通用安全设置，默认值与 TOPIAM 风格安全页面保持一致。
    public GeneralSecuritySettingsResponse general() {
        return new GeneralSecuritySettingsResponse(
            intValue("security.general.user_concurrent_sessions", -1),
            intValue("security.general.session_ttl_seconds", 18_000),
            intValue("security.general.remember_me_ttl_seconds", 604_800),
            intValue("security.general.captcha_ttl_minutes", 5),
            intValue("security.general.login_failure_window_minutes", 5),
            intValue("security.general.login_failure_max_attempts", authenticationPolicies.currentPasswordMaxFailureAttempts()),
            intValue("security.general.auto_unlock_minutes", 3),
            stringValue("security.general.content_security_policy", DEFAULT_CSP));
    }

    @Transactional
    // 保存控制台通用安全设置。
    public GeneralSecuritySettingsResponse updateGeneral(UpdateGeneralSecuritySettingsRequest request, String actor) {
        upsert("security.general.user_concurrent_sessions", GENERAL_CATEGORY, request.userConcurrentSessions(), false);
        upsert("security.general.session_ttl_seconds", GENERAL_CATEGORY, request.sessionTtlSeconds(), false);
        upsert("security.general.remember_me_ttl_seconds", GENERAL_CATEGORY, request.rememberMeTtlSeconds(), false);
        upsert("security.general.captcha_ttl_minutes", GENERAL_CATEGORY, request.captchaTtlMinutes(), false);
        upsert("security.general.login_failure_window_minutes", GENERAL_CATEGORY, request.loginFailureWindowMinutes(), false);
        upsert("security.general.login_failure_max_attempts", GENERAL_CATEGORY, request.loginFailureMaxAttempts(), false);
        upsert("security.general.auto_unlock_minutes", GENERAL_CATEGORY, request.autoUnlockMinutes(), false);
        upsert("security.general.content_security_policy", GENERAL_CATEGORY, request.contentSecurityPolicy(), false);
        auditService.record(actor, "security_settings.general.update", "security_settings", "general", "updated");
        return general();
    }

    @Transactional(readOnly = true)
    // 查询控制台密码策略页面设置。
    public PasswordPolicySettingsResponse passwordPolicy() {
        return new PasswordPolicySettingsResponse(
            intValue("security.password.min_length", authenticationPolicies.currentPasswordMinLength()),
            intValue("security.password.max_length", 20),
            stringValue("security.password.complexity", "three"),
            intValue("security.password.expires_in_days", authenticationPolicies.currentPasswordExpiresInDays()),
            intValue("security.password.expiry_reminder_days", 8),
            intValue("security.password.max_repeated_chars", 3),
            booleanValue("security.password.check_user_info", true),
            booleanValue("security.password.history_check_enabled", authenticationPolicies.currentPasswordHistoryCount() > 0),
            intValue("security.password.history_count", authenticationPolicies.currentPasswordHistoryCount()),
            booleanValue("security.password.illegal_sequence_check_enabled", false),
            booleanValue("security.password.weak_password_check_enabled", true),
            stringValue("security.password.additional_weak_passwords", ""),
            setValue("security.password.extension_rules", Set.of()));
    }

    @Transactional
    // 保存控制台密码策略页面设置，保留认证策略服务已有的运行时校验能力。
    public PasswordPolicySettingsResponse updatePasswordPolicy(UpdatePasswordPolicySettingsRequest request, String actor) {
        upsert("security.password.min_length", PASSWORD_CATEGORY, request.minLength(), false);
        upsert("security.password.max_length", PASSWORD_CATEGORY, request.maxLength(), false);
        upsert("security.password.complexity", PASSWORD_CATEGORY, request.complexity(), false);
        upsert("security.password.expires_in_days", PASSWORD_CATEGORY, request.passwordExpiresInDays(), false);
        upsert("security.password.expiry_reminder_days", PASSWORD_CATEGORY, request.expiryReminderDays(), false);
        upsert("security.password.max_repeated_chars", PASSWORD_CATEGORY, request.maxRepeatedChars(), false);
        upsert("security.password.check_user_info", PASSWORD_CATEGORY, request.checkUserInfo(), false);
        upsert("security.password.history_check_enabled", PASSWORD_CATEGORY, request.historyCheckEnabled(), false);
        upsert("security.password.history_count", PASSWORD_CATEGORY, request.passwordHistoryCount(), false);
        upsert("security.password.illegal_sequence_check_enabled", PASSWORD_CATEGORY, request.illegalSequenceCheckEnabled(), false);
        upsert("security.password.weak_password_check_enabled", PASSWORD_CATEGORY, request.weakPasswordCheckEnabled(), false);
        upsert("security.password.additional_weak_passwords", PASSWORD_CATEGORY, request.additionalWeakPasswords(), false);
        upsert("security.password.extension_rules", PASSWORD_CATEGORY, String.join(",", request.extensionRules() == null ? Set.of() : request.extensionRules()), false);
        auditService.record(actor, "security_settings.password.update", "security_settings", "password", "updated");
        return passwordPolicy();
    }

    private void upsert(String key, String category, Object value, boolean sensitive) {
        String settingValue = value == null ? "" : String.valueOf(value);
        settings.findBySettingKey(key)
            .ifPresentOrElse(
                setting -> setting.update(SettingValueType.STRING, settingValue, key, sensitive),
                () -> settings.save(new SystemSetting(key, category, SettingValueType.STRING, settingValue, key, sensitive)));
    }

    private int intValue(String key, int fallback) {
        String value = stringValue(key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean booleanValue(String key, boolean fallback) {
        String value = stringValue(key, null);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private String stringValue(String key, String fallback) {
        return settings.findBySettingKey(key)
            .map(SystemSetting::getSettingValue)
            .filter(value -> !value.isBlank())
            .orElse(fallback);
    }

    private Set<String> setValue(String key, Set<String> fallback) {
        String value = stringValue(key, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
