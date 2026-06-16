package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.dto.SecuritySettingDtos.GeneralSecuritySettingsResponse;
import com.antiam.dto.SecuritySettingDtos.PasswordPolicySettingsResponse;
import com.antiam.dto.SecuritySettingDtos.UpdateGeneralSecuritySettingsRequest;
import com.antiam.dto.SecuritySettingDtos.UpdatePasswordPolicySettingsRequest;
import com.antiam.service.SecuritySettingService;
import java.security.Principal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecuritySettingControllerTest {

    private final SecuritySettingService securitySettings = mock(SecuritySettingService.class);
    private final SecuritySettingController controller = new SecuritySettingController(securitySettings);
    private final Principal principal = () -> "admin";

    @Test
    void exposesGeneralSecuritySettings() {
        GeneralSecuritySettingsResponse settings = new GeneralSecuritySettingsResponse(-1, 18_000, 604_800, 5, 5, 3, 3, "default-src 'self'");
        when(securitySettings.general()).thenReturn(settings);

        assertThat(controller.general()).isEqualTo(settings);

        verify(securitySettings).general();
    }

    @Test
    void updatesGeneralSecuritySettings() {
        UpdateGeneralSecuritySettingsRequest request = new UpdateGeneralSecuritySettingsRequest(-1, 18_000, 604_800, 5, 5, 3, 3, "default-src 'self'");
        GeneralSecuritySettingsResponse settings = new GeneralSecuritySettingsResponse(-1, 18_000, 604_800, 5, 5, 3, 3, "default-src 'self'");
        when(securitySettings.updateGeneral(request, "admin")).thenReturn(settings);

        assertThat(controller.updateGeneral(request, principal)).isEqualTo(settings);

        verify(securitySettings).updateGeneral(request, "admin");
    }

    @Test
    void exposesPasswordPolicySettings() {
        PasswordPolicySettingsResponse settings = new PasswordPolicySettingsResponse(8, 20, "three", 7, 8, 3, true, true, 5, false, true, "", Set.of("serial-number"));
        when(securitySettings.passwordPolicy()).thenReturn(settings);

        assertThat(controller.passwordPolicy()).isEqualTo(settings);

        verify(securitySettings).passwordPolicy();
    }

    @Test
    void updatesPasswordPolicySettings() {
        UpdatePasswordPolicySettingsRequest request = new UpdatePasswordPolicySettingsRequest(8, 20, "three", 7, 8, 3, true, true, 5, false, true, "", Set.of("serial-number"));
        PasswordPolicySettingsResponse settings = new PasswordPolicySettingsResponse(8, 20, "three", 7, 8, 3, true, true, 5, false, true, "", Set.of("serial-number"));
        when(securitySettings.updatePasswordPolicy(request, "admin")).thenReturn(settings);

        assertThat(controller.updatePasswordPolicy(request, principal)).isEqualTo(settings);

        verify(securitySettings).updatePasswordPolicy(request, "admin");
    }
}
