package com.antiam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("id_token") String idToken,
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
        @JsonProperty("client_id") String clientId,
        String username,
        String sub,
        @JsonProperty("token_type") String tokenType,
        String scope,
        long exp
    ) {
    }

    public record UserInfoResponse(
        String sub,
        @JsonProperty("preferred_username") String preferredUsername,
        String name,
        String email,
        @JsonProperty("phone_number") String phoneNumber
    ) {
    }

    public record OidcDiscoveryResponse(
        String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("userinfo_endpoint") String userinfoEndpoint,
        @JsonProperty("introspection_endpoint") String introspectionEndpoint,
        @JsonProperty("revocation_endpoint") String revocationEndpoint,
        @JsonProperty("jwks_uri") String jwksUri,
        @JsonProperty("response_types_supported") List<String> responseTypesSupported,
        @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
        @JsonProperty("subject_types_supported") List<String> subjectTypesSupported,
        @JsonProperty("id_token_signing_alg_values_supported") List<String> idTokenSigningAlgValuesSupported,
        @JsonProperty("scopes_supported") List<String> scopesSupported,
        @JsonProperty("code_challenge_methods_supported") List<String> codeChallengeMethodsSupported,
        @JsonProperty("token_endpoint_auth_methods_supported") List<String> tokenEndpointAuthMethodsSupported,
        @JsonProperty("claims_supported") Map<String, Object> claimsSupported
    ) {
    }
}
