package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.dto.AccessDtos.ApplicationSsoConfigResponse;
import com.antiam.dto.AccessDtos.ConfigureApplicationSsoRequest;
import com.antiam.repository.ApplicationAccessRequestRepository;
import com.antiam.repository.ApplicationAssignmentRepository;
import com.antiam.repository.ApplicationGroupRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.CasServiceTicketRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthAuthorizationCodeRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.PermissionRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.SamlAssertionRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccessServiceTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationSsoConfigRepository ssoConfigs = mock(ApplicationSsoConfigRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessService service = new AccessService(
        mock(PermissionRepository.class),
        mock(RoleRepository.class),
        mock(UserGroupRepository.class),
        mock(ApplicationGroupRepository.class),
        applications,
        ssoConfigs,
        mock(ApplicationAccessRequestRepository.class),
        mock(ApplicationAssignmentRepository.class),
        mock(OAuthAccessTokenRepository.class),
        mock(OAuthRefreshTokenRepository.class),
        mock(OAuthAuthorizationCodeRepository.class),
        mock(OAuthConsentRepository.class),
        mock(SamlAssertionRepository.class),
        mock(CasServiceTicketRepository.class),
        mock(AuthenticationSessionRepository.class),
        mock(AuthenticationEventRepository.class),
        mock(UserAccountRepository.class),
        mock(TenantService.class),
        mock(AuditService.class),
        passwordEncoder);

    @Test
    void configuresApplicationProtocolSettings() {
        UUID applicationId = UUID.randomUUID();
        Application application = new Application("oidc", "OIDC", ApplicationProtocol.OIDC, "https://example.com/login", null, null);
        ConfigureApplicationSsoRequest request = new ConfigureApplicationSsoRequest(
            ApplicationProtocol.OIDC,
            "client-id",
            "client-secret",
            Set.of("https://example.com/callback"),
            Set.of("authorization_code", "refresh_token"),
            true,
            Set.of("https://example.com/logout"),
            "https://example.com/start",
            20,
            5,
            43_200,
            30,
            true,
            "RS256",
            Set.of("openid", "profile"),
            null,
            null,
            null,
            null,
            null,
            Set.of("email"),
            Map.of("tenant", "ant"));
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(passwordEncoder.encode("client-secret")).thenReturn("hashed-secret");
        when(ssoConfigs.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        when(ssoConfigs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationSsoConfigResponse response = service.configureApplicationSso(applicationId, request, "admin");

        assertThat(response.clientId()).isEqualTo("client-id");
        assertThat(response.redirectUris()).containsExactly("https://example.com/callback");
        assertThat(response.grantTypes()).containsExactlyInAnyOrder("authorization_code", "refresh_token");
        assertThat(response.pkceRequired()).isTrue();
        assertThat(response.postLogoutRedirectUris()).containsExactly("https://example.com/logout");
        assertThat(response.loginInitiationUri()).isEqualTo("https://example.com/start");
        assertThat(response.accessTokenTtlMinutes()).isEqualTo(20);
        assertThat(response.authorizationCodeTtlMinutes()).isEqualTo(5);
        assertThat(response.refreshTokenTtlMinutes()).isEqualTo(43_200);
        assertThat(response.idTokenTtlMinutes()).isEqualTo(30);
        assertThat(response.reuseRefreshTokens()).isTrue();
        assertThat(response.idTokenSignatureAlgorithm()).isEqualTo("RS256");
        assertThat(response.scopes()).containsExactlyInAnyOrder("openid", "profile");
    }
}
