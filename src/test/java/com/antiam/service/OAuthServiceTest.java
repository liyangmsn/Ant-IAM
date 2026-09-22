package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.TokenSupport;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthAuthorizationCodeRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class OAuthServiceTest {

    private final ApplicationSsoConfigRepository ssoConfigs = mock(ApplicationSsoConfigRepository.class);
    private final OAuthService service = new OAuthService(
        ssoConfigs,
        mock(OAuthAuthorizationCodeRepository.class),
        mock(OAuthAccessTokenRepository.class),
        mock(OAuthRefreshTokenRepository.class),
        mock(OAuthConsentRepository.class),
        mock(UserAccountRepository.class),
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
        when(config.getScopes()).thenReturn("openid profile");

        assertThatThrownBy(() -> service.authorize(
            "code",
            "client-1",
            "https://client.example.com/callback",
            "openid",
            "state-1",
            "too-short",
            "S256",
            "alice"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid code_challenge");
    }
}
