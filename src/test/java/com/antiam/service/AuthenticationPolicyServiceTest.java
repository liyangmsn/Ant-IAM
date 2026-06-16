package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.AuthenticationPolicy;
import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.mapper.AuthenticationPolicyMapper;
import com.antiam.repository.AuthenticationPolicyRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.SystemSettingRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthenticationPolicyServiceTest {

    private final AuthenticationPolicyRepository policies = mock(AuthenticationPolicyRepository.class);
    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final AuthenticationPolicyService service = new AuthenticationPolicyService(
        policies,
        settings,
        mock(UserAccountRepository.class),
        mock(MfaFactorRepository.class),
        mock(AuthenticationPolicyMapper.class),
        mock(RiskService.class),
        mock(AuditService.class));

    @Test
    void securitySettingsOverrideRuntimePasswordPolicyValues() {
        when(settings.findBySettingKey("security.password.min_length")).thenReturn(Optional.of(setting("security.password.min_length", "12")));
        when(settings.findBySettingKey("security.general.login_failure_max_attempts")).thenReturn(Optional.of(setting("security.general.login_failure_max_attempts", "4")));
        when(settings.findBySettingKey("security.password.expires_in_days")).thenReturn(Optional.of(setting("security.password.expires_in_days", "30")));
        when(settings.findBySettingKey("security.password.history_check_enabled")).thenReturn(Optional.of(setting("security.password.history_check_enabled", "true")));
        when(settings.findBySettingKey("security.password.history_count")).thenReturn(Optional.of(setting("security.password.history_count", "6")));

        assertThat(service.currentPasswordMinLength()).isEqualTo(12);
        assertThat(service.currentPasswordMaxFailureAttempts()).isEqualTo(4);
        assertThat(service.currentPasswordExpiresInDays()).isEqualTo(30);
        assertThat(service.currentPasswordHistoryCount()).isEqualTo(6);
    }

    @Test
    void disabledSecurityHistoryCheckOverridesPolicyHistoryCount() {
        AuthenticationPolicy policy = new AuthenticationPolicy("default", "Default", 100, false, false, 8, null, null);
        policy.updatePasswordHistoryPolicy(5);
        when(settings.findBySettingKey("security.password.history_check_enabled")).thenReturn(Optional.of(setting("security.password.history_check_enabled", "false")));
        when(policies.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(policy));

        assertThat(service.currentPasswordHistoryCount()).isZero();
    }

    @Test
    void fallsBackToEnabledAuthenticationPolicyWhenSecuritySettingMissing() {
        AuthenticationPolicy policy = new AuthenticationPolicy("default", "Default", 100, false, false, 10, null, null);
        policy.updatePasswordFailurePolicy(7);
        policy.updatePasswordExpiryPolicy(60);
        policy.updatePasswordHistoryPolicy(3);
        when(settings.findBySettingKey("security.password.min_length")).thenReturn(Optional.empty());
        when(settings.findBySettingKey("security.general.login_failure_max_attempts")).thenReturn(Optional.empty());
        when(settings.findBySettingKey("security.password.expires_in_days")).thenReturn(Optional.empty());
        when(settings.findBySettingKey("security.password.history_check_enabled")).thenReturn(Optional.empty());
        when(settings.findBySettingKey("security.password.history_count")).thenReturn(Optional.empty());
        when(policies.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of(policy));

        assertThat(service.currentPasswordMinLength()).isEqualTo(10);
        assertThat(service.currentPasswordMaxFailureAttempts()).isEqualTo(7);
        assertThat(service.currentPasswordExpiresInDays()).isEqualTo(60);
        assertThat(service.currentPasswordHistoryCount()).isEqualTo(3);
    }

    private SystemSetting setting(String key, String value) {
        return new SystemSetting(key, "security", SettingValueType.STRING, value, key, false);
    }
}
