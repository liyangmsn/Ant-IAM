package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.AuthenticationProvider;
import com.antiam.domain.AuthenticationProviderKind;
import com.antiam.domain.AuthenticationProviderType;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.UserAccount;
import com.antiam.common.TokenSupport;
import com.antiam.dto.AuthenticationDtos.ThirdPartyAuthorizeResponse;
import com.antiam.dto.AuthenticationDtos.ThirdPartyLoginCallbackRequest;
import com.antiam.dto.AuthenticationDtos.ThirdPartyLoginResponse;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationProviderRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserThirdPartyBindingRepository;
import com.antiam.service.thirdparty.ThirdPartyAuthAdapter;
import com.antiam.service.thirdparty.ThirdPartyProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThirdPartyLoginServiceTest {

    private final AuthenticationProviderRepository providers = mock(AuthenticationProviderRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UserThirdPartyBindingRepository bindings = mock(UserThirdPartyBindingRepository.class);
    private final AuthenticationSessionRepository sessions = mock(AuthenticationSessionRepository.class);
    private final AuthenticationEventRepository events = mock(AuthenticationEventRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final FakeAdapter adapter = new FakeAdapter();
    private final ThirdPartyLoginService service = new ThirdPartyLoginService(
        providers,
        users,
        bindings,
        sessions,
        events,
        auditService,
        new ClientMetadataService(mock(GeoIpService.class)),
        new ObjectMapper().findAndRegisterModules(),
        new TokenSupport(),
        List.of(adapter));

    @Test
    void buildsAuthorizationUrlFromEnabledProvider() {
        when(providers.findByProviderKey("wechat")).thenReturn(Optional.of(provider("wechat", AuthenticationProviderKind.WECHAT)));

        ThirdPartyAuthorizeResponse response = service.authorize("wechat", "https://iam.example.com/callback", "state-1");

        assertThat(response.providerKey()).isEqualTo("wechat");
        assertThat(response.state()).startsWith("v1.");
        assertThat(response.authorizationUrl()).startsWith("https://example.com/auth?redirect=https://iam.example.com/callback&state=v1.");
    }

    @Test
    void callbackCreatesLocalUserAndSession() {
        when(providers.findByProviderKey("wechat")).thenReturn(Optional.of(provider("wechat", AuthenticationProviderKind.WECHAT)));
        when(users.findByUsername("wechat_open-1")).thenReturn(Optional.empty());
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessions.save(any(AuthenticationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String signedState = service.authorize("wechat", "https://iam.example.com/callback", "state-1").state();

        ThirdPartyLoginResponse response = service.callback(
            "wechat",
            new ThirdPartyLoginCallbackRequest("code-1", signedState, "https://iam.example.com/callback"),
            "127.0.0.1",
            "Mozilla/5.0");

        assertThat(response.userCreated()).isTrue();
        assertThat(response.identity().subject()).isEqualTo("open-1");
        assertThat(response.session().sessionIndex()).startsWith("wechat:");
        verify(users).save(any(UserAccount.class));
        verify(sessions).save(any(AuthenticationSession.class));
    }

    @Test
    void callbackRejectsUnsignedStateByDefault() {
        when(providers.findByProviderKey("wechat")).thenReturn(Optional.of(provider("wechat", AuthenticationProviderKind.WECHAT)));

        assertThatThrownBy(() -> service.callback(
            "wechat",
            new ThirdPartyLoginCallbackRequest("code-1", "plain-state", "https://iam.example.com/callback"),
            "127.0.0.1",
            "Mozilla/5.0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not signed");
    }

    @Test
    void bindsThirdPartyIdentityToUser() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount("alice", "Alice", "alice@example.com", null, null, null);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(providers.findByProviderKey("wechat")).thenReturn(Optional.of(provider("wechat", AuthenticationProviderKind.WECHAT)));
        when(bindings.findByProviderKeyAndSubject("wechat", "open-1")).thenReturn(Optional.empty());
        when(bindings.findByUserIdAndProviderKey(userId, "wechat")).thenReturn(Optional.empty());
        when(bindings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String signedState = service.authorizeBinding(userId, "wechat", "https://iam.example.com/bind", "bind-1").state();

        var response = service.bind(
            userId,
            "wechat",
            new ThirdPartyLoginCallbackRequest("code-1", signedState, "https://iam.example.com/bind"),
            "admin");

        assertThat(response.providerKey()).isEqualTo("wechat");
        assertThat(response.subject()).isEqualTo("open-1");
        verify(bindings).save(any());
    }

    private AuthenticationProvider provider(String key, AuthenticationProviderKind kind) {
        return new AuthenticationProvider(
            key,
            key,
            kind,
            AuthenticationProviderType.SOCIAL,
            null,
            "{\"appId\":\"app\",\"appSecret\":\"secret\",\"redirectUri\":\"https://iam.example.com/callback\"}",
            true,
            true);
    }

    private static final class FakeAdapter implements ThirdPartyAuthAdapter {

        @Override
        public boolean supports(AuthenticationProviderKind provider) {
            return provider == AuthenticationProviderKind.WECHAT;
        }

        @Override
        public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
            return "https://example.com/auth?redirect=" + redirectUri + "&state=" + state;
        }

        @Override
        public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
            return new ThirdPartyProfile("open-1", "union-1", "Alice", "alice@example.com", "13800000000", null, Map.of());
        }
    }
}
