package com.antiam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OAuthDtos {
    private OAuthDtos() {
    }

    public record AuthorizationResponse(String redirectTo, String code, String state, boolean consentRequired, List<String> requestedScopes) {
    }

    public record GrantConsentRequest(
        @Schema(description = "OAuth 客户端 ID", example = "console-client")
        String clientId,
        @Schema(description = "授权范围列表", example = "[\"openid\", \"profile\", \"email\"]")
        List<String> scopes
    ) {
    }

    public record ConsentResponse(
        UUID id,
        UUID userId,
        String username,
        String clientId,
        String applicationCode,
        List<String> scopes,
        Instant grantedAt,
        Instant revokedAt,
        boolean active
    ) {
    }

    public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        String idToken,
        String scope
    ) {
    }

    public record OAuthTokenResponse(
        UUID id,
        String tokenType,
        UUID userId,
        String username,
        UUID applicationId,
        String applicationCode,
        String clientId,
        List<String> scopes,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant lastUsedAt,
        boolean active
    ) {
    }

    public record RevokeTokenRequest(
        @Schema(description = "需要撤销的刷新令牌")
        @NotBlank String refreshToken
    ) {
    }

    public record RevokeTokenResponse(boolean revoked) {
    }

    public record TokenIntrospectionResponse(
        boolean active,
        String clientId,
        String username,
        String sub,
        String tokenType,
        String scope,
        long exp
    ) {
    }

    public record UserInfoResponse(
        String sub,
        String preferredUsername,
        String name,
        String email,
        String phoneNumber
    ) {
    }

    public record OidcDiscoveryResponse(
        String issuer,
        String authorizationEndpoint,
        String tokenEndpoint,
        String userinfoEndpoint,
        String introspectionEndpoint,
        String revocationEndpoint,
        String jwksUri,
        List<String> responseTypesSupported,
        List<String> grantTypesSupported,
        List<String> subjectTypesSupported,
        List<String> idTokenSigningAlgValuesSupported,
        List<String> scopesSupported,
        List<String> codeChallengeMethodsSupported,
        List<String> tokenEndpointAuthMethodsSupported,
        Map<String, Object> claimsSupported
    ) {
    }
}
