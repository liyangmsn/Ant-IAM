package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyAuthorizeResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyBindingResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyIdentityResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyLoginCallbackRequest;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyLoginResponse;

import com.antiam.common.TokenSupport;
import com.antiam.common.NotFoundException;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationProvider;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserThirdPartyBinding;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationProviderRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserThirdPartyBindingRepository;
import com.antiam.service.thirdparty.ThirdPartyAuthAdapter;
import com.antiam.service.thirdparty.ThirdPartyAuthSupport;
import com.antiam.service.thirdparty.ThirdPartyProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ThirdPartyLoginService {

    private static final Pattern USERNAME_UNSAFE = Pattern.compile("[^a-zA-Z0-9_.@-]");

    private final AuthenticationProviderRepository providers;
    private final UserAccountRepository users;
    private final UserThirdPartyBindingRepository bindings;
    private final AuthenticationSessionRepository sessions;
    private final AuthenticationEventRepository events;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TokenSupport tokenSupport;
    private final List<ThirdPartyAuthAdapter> adapters;

    @Transactional(readOnly = true)
    public ThirdPartyAuthorizeResponse authorize(String providerKey, String redirectUri, String state) {
        AuthenticationProvider provider = getEnabledProvider(providerKey);
        JsonNode configuration = readConfiguration(provider);
        String resolvedRedirectUri = ThirdPartyAuthSupport.redirectUri(configuration, redirectUri);
        String resolvedState = signedState(provider, configuration, resolvedRedirectUri, state, "login", null);
        ThirdPartyAuthAdapter adapter = adapter(provider);
        return new ThirdPartyAuthorizeResponse(
            provider.getProviderKey(),
            adapter.authorizationUrl(configuration, resolvedRedirectUri, resolvedState),
            resolvedState);
    }

    @Transactional
    public ThirdPartyLoginResponse callback(String providerKey, ThirdPartyLoginCallbackRequest request, String ipAddress, String userAgent) {
        AuthenticationProvider provider = getEnabledProvider(providerKey);
        JsonNode configuration = readConfiguration(provider);
        StateValidation state = validateState(provider, configuration, request, "login", null);
        String resolvedRedirectUri = state.redirectUri();
        ThirdPartyProfile profile = adapter(provider).exchange(configuration, request.code(), resolvedRedirectUri);
        UserResolution resolution = resolveUser(provider, configuration, profile);
        AuthenticationSession session = sessions.save(new AuthenticationSession(
            resolution.user(),
            null,
            ApplicationProtocol.OAUTH2,
            provider.getProviderKey() + ":" + UUID.randomUUID(),
            ipAddress,
            userAgent,
            Instant.now().plus(sessionTtl(configuration))));
        events.save(new AuthenticationEvent(
            session,
            resolution.user(),
            null,
            AuthenticationEventType.LOGIN_SUCCESS,
            ipAddress,
            userAgent,
            "Third-party login via " + provider.getProviderKey() + ", subject=" + profile.subject()));
        auditService.record(resolution.user().getUsername(), "third_party_login.success", "authentication_provider", String.valueOf(provider.getId()), provider.getProviderKey());
        return new ThirdPartyLoginResponse(
            new ThirdPartyIdentityResponse(
                provider.getProviderKey(),
                provider.getProvider().name(),
                profile.subject(),
                profile.unionId(),
                profile.displayName(),
                profile.email(),
                profile.mobile(),
                profile.avatarUrl(),
                profile.raw() == null ? Map.of() : profile.raw()),
            toResponse(session),
            resolution.user().getId(),
            resolution.created());
    }

    @Transactional(readOnly = true)
    public List<ThirdPartyBindingResponse> bindings(UUID userId) {
        return bindings.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(this::toBindingResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ThirdPartyAuthorizeResponse authorizeBinding(UUID userId, String providerKey, String redirectUri, String state) {
        ensureUser(userId);
        AuthenticationProvider provider = getEnabledProvider(providerKey);
        JsonNode configuration = readConfiguration(provider);
        String resolvedRedirectUri = ThirdPartyAuthSupport.redirectUri(configuration, redirectUri);
        String resolvedState = signedState(provider, configuration, resolvedRedirectUri, state, "bind", userId);
        return new ThirdPartyAuthorizeResponse(
            provider.getProviderKey(),
            adapter(provider).authorizationUrl(configuration, resolvedRedirectUri, resolvedState),
            resolvedState);
    }

    @Transactional
    public ThirdPartyBindingResponse bind(UUID userId, String providerKey, ThirdPartyLoginCallbackRequest request, String actor) {
        UserAccount user = ensureUser(userId);
        AuthenticationProvider provider = getEnabledProvider(providerKey);
        JsonNode configuration = readConfiguration(provider);
        StateValidation state = validateState(provider, configuration, request, "bind", userId);
        ThirdPartyProfile profile = adapter(provider).exchange(configuration, request.code(), state.redirectUri());
        UserThirdPartyBinding binding = bindings.findByProviderKeyAndSubject(provider.getProviderKey(), profile.subject())
            .map(existing -> {
                if (!existing.getUser().getId().equals(userId)) {
                    throw new IllegalArgumentException("Third-party identity is already bound to another user");
                }
                return existing;
            })
            .orElseGet(() -> bindings.findByUserIdAndProviderKey(userId, provider.getProviderKey())
                .orElseGet(() -> new UserThirdPartyBinding(
                    user,
                    provider.getProviderKey(),
                    provider.getProvider(),
                    profile.subject(),
                    profile.unionId(),
                    profile.displayName(),
                    profile.email(),
                    profile.mobile(),
                    profile.avatarUrl(),
                    raw(profile))));
        binding.updateProfile(profile.unionId(), profile.displayName(), profile.email(), profile.mobile(), profile.avatarUrl(), raw(profile));
        UserThirdPartyBinding saved = bindings.save(binding);
        auditService.record(actor, "third_party_binding.upsert", "user", String.valueOf(userId), provider.getProviderKey() + ":" + profile.subject());
        return toBindingResponse(saved);
    }

    @Transactional
    public void unbind(UUID userId, UUID bindingId, String actor) {
        UserThirdPartyBinding binding = bindings.findById(bindingId)
            .filter(candidate -> candidate.getUser().getId().equals(userId))
            .orElseThrow(() -> new NotFoundException("Third-party binding not found: " + bindingId));
        bindings.delete(binding);
        auditService.record(actor, "third_party_binding.delete", "user", String.valueOf(userId), binding.getProviderKey() + ":" + binding.getSubject());
    }

    private AuthenticationProvider getEnabledProvider(String providerKey) {
        AuthenticationProvider provider = providers.findByProviderKey(providerKey)
            .orElseThrow(() -> new NotFoundException("Authentication provider not found: " + providerKey));
        if (!provider.isEnabled()) {
            throw new IllegalArgumentException("Authentication provider is disabled: " + providerKey);
        }
        return provider;
    }

    private ThirdPartyAuthAdapter adapter(AuthenticationProvider provider) {
        return adapters.stream()
            .filter(candidate -> candidate.supports(provider.getProvider()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported third-party login provider: " + provider.getProvider()));
    }

    private JsonNode readConfiguration(AuthenticationProvider provider) {
        if (provider.getConfiguration() == null || provider.getConfiguration().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(provider.getConfiguration());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Authentication provider configuration must be valid JSON: " + provider.getProviderKey(), ex);
        }
    }

    private UserResolution resolveUser(AuthenticationProvider provider, JsonNode configuration, ThirdPartyProfile profile) {
        String username = username(provider, configuration, profile);
        return users.findByUsername(username)
            .map(user -> {
                user.updateProfile(displayName(profile, username), profile.email(), profile.mobile(), user.getOrganization());
                return new UserResolution(user, false);
            })
            .orElseGet(() -> {
                if (!ThirdPartyAuthSupport.boolOrDefault(configuration, "autoCreateUser", true)) {
                    throw new NotFoundException("Local user not found for third-party subject: " + username);
                }
                UserAccount created = users.save(new UserAccount(
                    username,
                    displayName(profile, username),
                    profile.email(),
                    profile.mobile(),
                    null,
                    null));
                return new UserResolution(created, true);
            });
    }

    private String username(AuthenticationProvider provider, JsonNode configuration, ThirdPartyProfile profile) {
        String usernameClaim = ThirdPartyAuthSupport.text(configuration, "usernameClaim");
        String candidate = switch (usernameClaim == null ? "" : usernameClaim) {
            case "unionId" -> profile.unionId();
            case "email" -> profile.email();
            case "mobile" -> profile.mobile();
            default -> profile.subject();
        };
        if (candidate == null || candidate.isBlank()) {
            candidate = profile.subject();
        }
        String prefix = ThirdPartyAuthSupport.textOrDefault(configuration, "usernamePrefix", provider.getProviderKey() + "_");
        return prefix + USERNAME_UNSAFE.matcher(candidate).replaceAll("_");
    }

    private String displayName(ThirdPartyProfile profile, String username) {
        return profile.displayName() == null || profile.displayName().isBlank() ? username : profile.displayName();
    }

    private Duration sessionTtl(JsonNode configuration) {
        long minutes = configuration.has("sessionTtlMinutes") ? configuration.path("sessionTtlMinutes").asLong(480) : 480;
        return Duration.ofMinutes(minutes);
    }

    private UserAccount ensureUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private String signedState(AuthenticationProvider provider, JsonNode configuration, String redirectUri, String clientState, String mode, UUID userId) {
        ObjectNode payload = objectMapper.createObjectNode()
            .put("providerKey", provider.getProviderKey())
            .put("redirectUri", redirectUri)
            .put("nonce", tokenSupport.generateToken(16))
            .put("clientState", ThirdPartyAuthSupport.state(clientState))
            .put("mode", mode)
            .put("issuedAt", Instant.now().getEpochSecond());
        if (userId != null) {
            payload.put("userId", userId.toString());
        }
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return "v1." + encodedPayload + "." + signature(configuration, encodedPayload);
    }

    private StateValidation validateState(AuthenticationProvider provider, JsonNode configuration, ThirdPartyLoginCallbackRequest request, String expectedMode, UUID expectedUserId) {
        String configuredRedirectUri = ThirdPartyAuthSupport.redirectUri(configuration, request.redirectUri());
        String state = request.state();
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Third-party login state is required");
        }
        if (!state.startsWith("v1.")) {
            if (ThirdPartyAuthSupport.boolOrDefault(configuration, "allowUnsignedState", false)) {
                return new StateValidation(configuredRedirectUri, expectedMode, expectedUserId);
            }
            throw new IllegalArgumentException("Third-party login state is not signed");
        }
        String[] parts = state.split("\\.", 3);
        if (parts.length != 3 || !signature(configuration, parts[1]).equals(parts[2])) {
            throw new IllegalArgumentException("Third-party login state signature is invalid");
        }
        try {
            JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            String providerKey = payload.path("providerKey").asText();
            String redirectUri = payload.path("redirectUri").asText();
            String mode = payload.path("mode").asText("login");
            long issuedAt = payload.path("issuedAt").asLong(0);
            if (!provider.getProviderKey().equals(providerKey)) {
                throw new IllegalArgumentException("Third-party login state provider does not match");
            }
            if (!configuredRedirectUri.equals(redirectUri)) {
                throw new IllegalArgumentException("Third-party login state redirectUri does not match");
            }
            if (!expectedMode.equals(mode)) {
                throw new IllegalArgumentException("Third-party login state mode does not match");
            }
            UUID userId = payload.hasNonNull("userId") ? UUID.fromString(payload.path("userId").asText()) : null;
            if (expectedUserId != null && !expectedUserId.equals(userId)) {
                throw new IllegalArgumentException("Third-party login state user does not match");
            }
            long ttlSeconds = configuration.has("stateTtlSeconds") ? configuration.path("stateTtlSeconds").asLong(600) : 600;
            if (issuedAt <= 0 || Instant.ofEpochSecond(issuedAt).plusSeconds(ttlSeconds).isBefore(Instant.now())) {
                throw new IllegalArgumentException("Third-party login state has expired");
            }
            return new StateValidation(redirectUri, mode, userId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw new IllegalArgumentException("Third-party login state is invalid", ex);
        }
    }

    private String signature(JsonNode configuration, String encodedPayload) {
        return tokenSupport.sha256(encodedPayload + "." + ThirdPartyAuthSupport.required(configuration, "appSecret"));
    }

    private ThirdPartyBindingResponse toBindingResponse(UserThirdPartyBinding binding) {
        return new ThirdPartyBindingResponse(
            binding.getId(),
            binding.getUser().getId(),
            binding.getProviderKey(),
            binding.getProvider().name(),
            binding.getSubject(),
            binding.getUnionId(),
            binding.getDisplayName(),
            binding.getEmail(),
            binding.getMobile(),
            binding.getAvatarUrl(),
            binding.getCreatedAt(),
            binding.getUpdatedAt());
    }

    private String raw(ThirdPartyProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile.raw() == null ? Map.of() : profile.raw());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Third-party profile raw payload must be serializable", ex);
        }
    }

    private AuthenticationSessionResponse toResponse(AuthenticationSession session) {
        return new AuthenticationSessionResponse(
            session.getId(),
            session.getUser() == null ? null : session.getUser().getId(),
            null,
            session.getProtocol(),
            session.getSessionIndex(),
            session.getIpAddress(),
            session.getUserAgent(),
            null,
            deviceType(session.getUserAgent()),
            session.getCreatedAt(),
            session.getUpdatedAt(),
            session.getExpiresAt(),
            session.getEndedAt(),
            session.isActive());
    }

    private String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String normalized = userAgent.toLowerCase();
        if (normalized.contains("mobile") || normalized.contains("iphone") || normalized.contains("android")) {
            return "Mobile";
        }
        if (normalized.contains("ipad") || normalized.contains("tablet")) {
            return "Tablet";
        }
        return "PC";
    }

    private record UserResolution(UserAccount user, boolean created) {
    }

    private record StateValidation(String redirectUri, String mode, UUID userId) {
    }
}
