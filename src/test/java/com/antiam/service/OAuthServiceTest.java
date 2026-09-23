package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.TokenSupport;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.UserAccount;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthAuthorizationCodeRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class OAuthServiceTest {

    private final ApplicationSsoConfigRepository ssoConfigs = mock(ApplicationSsoConfigRepository.class);
    private final OAuthConsentRepository consents = mock(OAuthConsentRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final OAuthService service = new OAuthService(
        ssoConfigs,
        mock(OAuthAuthorizationCodeRepository.class),
        mock(OAuthAccessTokenRepository.class),
        mock(OAuthRefreshTokenRepository.class),
        consents,
        users,
        mock(AuthenticationEventRepository.class),
        new TokenSupport(),
        mock(JwtService.class),
        mock(PasswordEncoder.class),
        mock(AuditService.class));

    @Test
    void rejectsMalformedS256ChallengeBeforeIssuingAuthorizationCode() {
        Application application = mock(Application.class);
        ApplicationSsoConfig config = mock(ApplicationSsoConfig.class);
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));
        when(config.isEnabled()).thenReturn(true);
        when(config.getApplication()).thenReturn(application);
        when(application.isEnabled()).thenReturn(true);
        when(config.getProtocol()).thenReturn(ApplicationProtocol.OIDC);
        when(config.getRedirectUris()).thenReturn("https://client.example.com/callback");
        when(config.getScopes()).thenReturn("openid\nprofile");

        assertThatThrownBy(() -> service.authorize(
            "code",
            "client-1",
            "https://client.example.com/callback",
            "openid",
            "state-1",
            null,
            "too-short",
            "S256",
            "alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid code_challenge");
    }

    @Test
    void publishesInteractiveBrowserAuthorizationEndpoint() {
        assertThat(service.discovery("https://iam.example.com:31072/").authorizationEndpoint())
            .isEqualTo("https://iam.example.com:31072/oidc/authorize");
    }

    @Test
    void preservesNonceWhenRedirectingToConsent() {
        Application application = mock(Application.class);
        ApplicationSsoConfig config = mock(ApplicationSsoConfig.class);
        UserAccount user = mock(UserAccount.class);
        UUID userId = UUID.randomUUID();
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));
        when(config.isEnabled()).thenReturn(true);
        when(config.getApplication()).thenReturn(application);
        when(application.isEnabled()).thenReturn(true);
        when(config.getProtocol()).thenReturn(ApplicationProtocol.OIDC);
        when(config.getRedirectUris()).thenReturn("https://client.example.com/callback");
        when(config.getScopes()).thenReturn("openid\nprofile");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(consents.findByClientIdAndUserId("client-1", userId)).thenReturn(Optional.empty());
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

        var response = service.authorize(
            "code",
            "client-1",
            "https://client.example.com/callback",
            "openid",
            "state-1",
            "client-nonce-1",
            challenge,
            "S256",
            "alice");

        assertThat(response.consentRequired()).isTrue();
        assertThat(response.redirectTo()).contains("nonce=client-nonce-1");
    }
}
