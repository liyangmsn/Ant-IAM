package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.UserAccount;
import com.antiam.dto.FederationDtos.JwtSsoTokenResponse;
import com.antiam.dto.FederationDtos.JwtSsoVerificationResponse;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.CasServiceTicketRepository;
import com.antiam.repository.SamlAssertionRepository;
import com.antiam.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FederationServiceTest {

    private final ApplicationSsoConfigRepository ssoConfigs = mock(ApplicationSsoConfigRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final AuthenticationEventRepository authenticationEvents = mock(AuthenticationEventRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final FederationService service = new FederationService(
        ssoConfigs,
        mock(SamlAssertionRepository.class),
        mock(CasServiceTicketRepository.class),
        users,
        authenticationEvents,
        new TokenSupport(),
        jwtService);

    @Test
    void issuesJwtSsoTokenWithConfiguredAudienceAndTtl() {
        UUID userId = UUID.randomUUID();
        ApplicationSsoConfig config = jwtConfig(
            ApplicationProtocol.JWT,
            "client-1",
            "checkout-audience",
            "preferred_username\nemail",
            "dept=finance",
            20);
        when(ssoConfigs.findByJwtAudience("checkout-audience")).thenReturn(Optional.of(config));
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("alice");
        when(user.getEmail()).thenReturn("alice@example.com");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.signToken(anyString(), anyString(), anyString(), any(), any(), any())).thenReturn("signed-token");

        JwtSsoTokenResponse response = service.issueJwtSsoToken("checkout-audience", "alice", "https://iam.example.com");

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.issuer()).isEqualTo("https://iam.example.com");
        assertThat(response.audience()).isEqualTo("checkout-audience");
        assertThat(response.subject()).isEqualTo("alice");
        assertThat(response.expiresAt()).isEqualTo(response.issuedAt().plusSeconds(20 * 60));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwtService).signToken(
            eq("https://iam.example.com"),
            eq(userId.toString()),
            eq("checkout-audience"),
            any(Instant.class),
            any(Instant.class),
            claims.capture());
        assertThat(claims.getValue())
            .containsEntry("preferred_username", "alice")
            .containsEntry("email", "alice@example.com")
            .containsEntry("dept", "finance")
            .doesNotContainKey("phone_number");
        verify(authenticationEvents).save(any(AuthenticationEvent.class));
    }

    @Test
    void fallsBackToClientIdLookup() {
        ApplicationSsoConfig config = jwtConfig(ApplicationProtocol.JWT, "client-1", null, null, null, 5);
        when(ssoConfigs.findByJwtAudience("client-1")).thenReturn(Optional.empty());
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.signToken(anyString(), anyString(), anyString(), any(), any(), any())).thenReturn("signed-token");

        JwtSsoTokenResponse response = service.issueJwtSsoToken("client-1", "alice", "https://iam.example.com");

        assertThat(response.audience()).isEqualTo("client-1");
    }

    @Test
    void rejectsApplicationNotConfiguredForJwt() {
        ApplicationSsoConfig config = jwtConfig(ApplicationProtocol.OIDC, "client-1", null, null, null, 5);
        when(ssoConfigs.findByJwtAudience("client-1")).thenReturn(Optional.empty());
        when(ssoConfigs.findByClientId("client-1")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.issueJwtSsoToken("client-1", "alice", "https://iam.example.com"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JWT single sign-on");
    }

    @Test
    void rejectsUnknownAudience() {
        when(ssoConfigs.findByJwtAudience("nope")).thenReturn(Optional.empty());
        when(ssoConfigs.findByClientId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueJwtSsoToken("nope", "alice", "https://iam.example.com"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void mapsVerificationResult() {
        Instant issuedAt = Instant.now();
        when(jwtService.verify("token")).thenReturn(new JwtService.TokenVerification(
            true,
            "kid-1",
            "https://iam.example.com",
            "checkout-app",
            "alice",
            issuedAt,
            issuedAt.plusSeconds(600),
            Map.of("email", "alice@example.com"),
            null,
            null));

        JwtSsoVerificationResponse response = service.verifyJwtSsoToken("token");

        assertThat(response.valid()).isTrue();
        assertThat(response.keyId()).isEqualTo("kid-1");
        assertThat(response.audience()).isEqualTo("checkout-app");
        assertThat(response.expiresAt()).isEqualTo(issuedAt.plusSeconds(600));
        assertThat(response.claims()).containsEntry("email", "alice@example.com");
    }

    private static ApplicationSsoConfig jwtConfig(
        ApplicationProtocol protocol,
        String clientId,
        String jwtAudience,
        String idTokenClaims,
        String customClaims,
        int accessTokenTtlMinutes
    ) {
        Application application = new Application("checkout", "Checkout", protocol, "https://checkout.example.com", "checkout app", null, null);
        if (protocol == ApplicationProtocol.OIDC) {
            application = new Application("oidc", "OIDC", protocol, "https://checkout.example.com", "oidc app", null, null);
        }
        return new ApplicationSsoConfig(
            application,
            protocol,
            clientId,
            null,
            null,
            null,
            false,
            null,
            null,
            accessTokenTtlMinutes,
            5,
            43_200,
            30,
            false,
            "RS256",
            null,
            null,
            null,
            null,
            jwtAudience,
            null,
            idTokenClaims,
            customClaims);
    }
}
