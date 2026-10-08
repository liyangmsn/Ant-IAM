package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.TokenSupport;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.OAuthAccessToken;
import com.antiam.domain.UserAccount;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDecisionResponse;
import com.antiam.dto.ApplicationPermissionDtos.PermissionCheckRequest;
import com.antiam.dto.ApplicationPermissionDtos.PermissionCheckResponse;
import java.time.Instant;
import java.util.List;
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
    private final OAuthAccessTokenRepository accessTokens = mock(OAuthAccessTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessService access = mock(AccessService.class);
    private final TokenSupport tokens = new TokenSupport();
    private final OAuthService service = new OAuthService(
        ssoConfigs,
        mock(OAuthAuthorizationCodeRepository.class),
        accessTokens,
        mock(OAuthRefreshTokenRepository.class),
        consents,
        users,
        mock(AuthenticationEventRepository.class),
        tokens,
        mock(JwtService.class),
        passwordEncoder,
        mock(AuditService.class),
        access,
        mock(ApplicationPermissionService.class));

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

    @Test
    void allowsAuthorizationWithoutPkceWhenClientDoesNotRequireIt() {
        Application application = mock(Application.class);
        ApplicationSsoConfig config = mock(ApplicationSsoConfig.class);
        UserAccount user = mock(UserAccount.class);
        UUID userId = UUID.randomUUID();
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));
        when(config.isEnabled()).thenReturn(true);
        when(config.isPkceRequired()).thenReturn(false);
        when(config.getApplication()).thenReturn(application);
        when(application.isEnabled()).thenReturn(true);
        when(config.getProtocol()).thenReturn(ApplicationProtocol.OIDC);
        when(config.getRedirectUris()).thenReturn("https://client.example.com/callback");
        when(config.getScopes()).thenReturn("openid\nprofile");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(consents.findByClientIdAndUserId("client-1", userId)).thenReturn(Optional.empty());

        var response = service.authorize(
            "code",
            "client-1",
            "https://client.example.com/callback",
            "openid",
            "state-1",
            null,
            null,
            null,
            "alice");

        assertThat(response.consentRequired()).isTrue();
    }

    @Test
    void checksApplicationPermissionsForClientIssuedToken() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserAccount user = activeUser(userId);
        Application application = oauthClient(applicationId);
        OAuthAccessToken token = accessToken("client-1", application, user);
        when(accessTokens.findByTokenHash(tokens.sha256("user-token"))).thenReturn(Optional.of(token));
        when(access.decideApplicationPermissions(applicationId, userId)).thenReturn(
            new ApplicationPermissionDecisionResponse(applicationId, userId, true, "direct_assignment", List.of("order:read", "order:approve")));

        PermissionCheckResponse allowed = service.checkPermissions("client-1", "secret",
            new PermissionCheckRequest("user-token", List.of("order:approve")));
        PermissionCheckResponse denied = service.checkPermissions("client-1", "secret",
            new PermissionCheckRequest("user-token", List.of("order:read", "order:delete")));

        assertThat(allowed.active()).isTrue();
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.sub()).isEqualTo(userId.toString());
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reason()).isEqualTo("permission_denied");
        assertThat(denied.results()).containsEntry("order:read", true).containsEntry("order:delete", false);
    }

    @Test
    void rejectsPermissionCheckForTokenIssuedToAnotherClient() {
        UUID applicationId = UUID.randomUUID();
        Application application = oauthClient(applicationId);
        OAuthAccessToken token = accessToken("other-client", application, activeUser(UUID.randomUUID()));
        when(accessTokens.findByTokenHash(tokens.sha256("user-token"))).thenReturn(Optional.of(token));

        PermissionCheckResponse response = service.checkPermissions("client-1", "secret",
            new PermissionCheckRequest("user-token", List.of("order:approve")));

        assertThat(response.active()).isFalse();
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("token_inactive");
        assertThat(response.results()).containsEntry("order:approve", false);
    }

    @Test
    void deniesPermissionCheckWhenUserLostApplicationAccess() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Application application = oauthClient(applicationId);
        OAuthAccessToken token = accessToken("client-1", application, activeUser(userId));
        when(accessTokens.findByTokenHash(tokens.sha256("user-token"))).thenReturn(Optional.of(token));
        when(access.decideApplicationPermissions(applicationId, userId)).thenReturn(
            new ApplicationPermissionDecisionResponse(applicationId, userId, false, "no_assignment", List.of()));

        PermissionCheckResponse response = service.checkPermissions("client-1", "secret",
            new PermissionCheckRequest("user-token", List.of("order:approve")));

        assertThat(response.active()).isTrue();
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("no_assignment");
    }

    private Application oauthClient(UUID applicationId) {
        Application application = mock(Application.class);
        ApplicationSsoConfig config = mock(ApplicationSsoConfig.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.isEnabled()).thenReturn(true);
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));
        when(config.isEnabled()).thenReturn(true);
        when(config.getApplication()).thenReturn(application);
        when(config.getProtocol()).thenReturn(ApplicationProtocol.OIDC);
        when(config.getClientSecretHash()).thenReturn("hash");
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        return application;
    }

    private static UserAccount activeUser(UUID userId) {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("alice");
        when(user.getStatus()).thenReturn(AccountStatus.ACTIVE);
        return user;
    }

    private static OAuthAccessToken accessToken(String clientId, Application application, UserAccount user) {
        OAuthAccessToken token = mock(OAuthAccessToken.class);
        when(token.getClientId()).thenReturn(clientId);
        when(token.getApplication()).thenReturn(application);
        when(token.getUser()).thenReturn(user);
        when(token.isActive(org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(true);
        return token;
    }
}
