package com.antiam.service;

import static com.antiam.dto.AuthenticationProviderDtos.AuthenticationProviderResponse;
import static com.antiam.dto.AuthenticationProviderDtos.CreateAuthenticationProviderRequest;
import static com.antiam.dto.AuthenticationProviderDtos.UpdateAuthenticationProviderRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.AuthenticationProvider;
import com.antiam.domain.AuthenticationProviderKind;
import com.antiam.domain.AuthenticationProviderType;
import com.antiam.repository.AuthenticationProviderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationProviderService {

    private final AuthenticationProviderRepository providers;
    private final AuditService auditService;

    @Transactional
    public AuthenticationProviderResponse create(CreateAuthenticationProviderRequest request, String actor) {
        AuthenticationProvider saved = providers.save(new AuthenticationProvider(
            request.providerKey(),
            request.name(),
            request.provider(),
            request.type(),
            request.description(),
            request.configuration(),
            request.visible(),
            request.enabled()));
        auditService.record(actor, "authentication_provider.create", "authentication_provider", saved.getId().toString(), saved.getProviderKey());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AuthenticationProviderResponse> list(AuthenticationProviderType type, AuthenticationProviderKind provider, Boolean enabled, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        List<AuthenticationProvider> values = type == null ? providers.findAll() : providers.findByTypeOrderByCreatedAtAsc(type);
        return values.stream()
            .filter(item -> provider == null || item.getProvider() == provider)
            .filter(item -> enabled == null || item.isEnabled() == enabled)
            .filter(item -> normalizedKeyword == null || matchesKeyword(item, normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public AuthenticationProviderResponse get(UUID providerId) {
        return toResponse(getEntity(providerId));
    }

    @Transactional
    public AuthenticationProviderResponse update(UUID providerId, UpdateAuthenticationProviderRequest request, String actor) {
        AuthenticationProvider provider = getEntity(providerId);
        provider.update(
            request.name(),
            request.provider(),
            request.type(),
            request.description(),
            request.configuration(),
            request.visible());
        auditService.record(actor, "authentication_provider.update", "authentication_provider", providerId.toString(), provider.getProviderKey());
        return toResponse(provider);
    }

    @Transactional
    public AuthenticationProviderResponse enable(UUID providerId, String actor) {
        AuthenticationProvider provider = getEntity(providerId);
        provider.enable();
        auditService.record(actor, "authentication_provider.enable", "authentication_provider", providerId.toString(), provider.getProviderKey());
        return toResponse(provider);
    }

    @Transactional
    public AuthenticationProviderResponse disable(UUID providerId, String actor) {
        AuthenticationProvider provider = getEntity(providerId);
        provider.disable();
        auditService.record(actor, "authentication_provider.disable", "authentication_provider", providerId.toString(), provider.getProviderKey());
        return toResponse(provider);
    }

    @Transactional
    public void delete(UUID providerId, String actor) {
        AuthenticationProvider provider = getEntity(providerId);
        String key = provider.getProviderKey();
        providers.delete(provider);
        auditService.record(actor, "authentication_provider.delete", "authentication_provider", providerId.toString(), key);
    }

    private AuthenticationProvider getEntity(UUID providerId) {
        return providers.findById(providerId)
            .orElseThrow(() -> new NotFoundException("Authentication provider not found: " + providerId));
    }

    private AuthenticationProviderResponse toResponse(AuthenticationProvider provider) {
        return new AuthenticationProviderResponse(
            provider.getId(),
            provider.getProviderKey(),
            provider.getName(),
            provider.getProvider(),
            provider.getType(),
            provider.getDescription(),
            maskSensitiveConfiguration(provider.getConfiguration()),
            provider.isVisible(),
            provider.isEnabled(),
            provider.getCreatedAt(),
            provider.getUpdatedAt());
    }

    private String maskSensitiveConfiguration(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return configuration;
        }
        return configuration
            .replaceAll("(?i)(\"(?:appSecret|secret|secretKey|clientSecret|password)\"\\s*:\\s*\")([^\"]+)(\")", "$1******$3");
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesKeyword(AuthenticationProvider provider, String keyword) {
        return contains(provider.getProviderKey(), keyword)
            || contains(provider.getName(), keyword)
            || contains(provider.getDescription(), keyword)
            || provider.getProvider().name().toLowerCase().contains(keyword)
            || provider.getType().name().toLowerCase().contains(keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
