package com.antiam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;

public final class FederationDtos {
    private FederationDtos() {
    }

    public record SamlMetadataResponse(String entityId, String ssoUrl, String certificateUse, String protocol) {
    }

    public record SamlAssertionResponse(
        String assertionId,
        String issuer,
        String audience,
        String acsUrl,
        String subject,
        Instant notBefore,
        Instant notOnOrAfter,
        Map<String, String> attributes
    ) {
    }

    public record CasLoginResponse(String redirectTo, String ticket, String service) {
    }

    public record CasServiceValidationResponse(
        boolean success,
        String user,
        String service,
        Map<String, String> attributes,
        String failureCode,
        String failureMessage
    ) {
    }

    public record JwtSsoTokenResponse(
        @Schema(description = "签发的 JWT 访问令牌") String accessToken,
        @Schema(description = "令牌类型，固定为 Bearer", example = "Bearer") String tokenType,
        @Schema(description = "签发方标识，取自请求根地址") String issuer,
        @Schema(description = "令牌 audience，取自应用 SSO 配置的 JWT Audience") String audience,
        @Schema(description = "令牌主体，取用户账号") String subject,
        Instant issuedAt,
        Instant expiresAt
    ) {
    }

    public record VerifyJwtTokenRequest(
        @Schema(description = "待校验的 JWT 令牌") @NotBlank String token
    ) {
    }

    public record JwtSsoVerificationResponse(
        @Schema(description = "签名与有效期是否通过校验") boolean valid,
        @Schema(description = "命中的签名密钥 keyId") String keyId,
        String issuer,
        String audience,
        String subject,
        Instant issuedAt,
        Instant expiresAt,
        @Schema(description = "令牌声明集合，校验失败时为空") Map<String, Object> claims,
        @Schema(description = "校验失败码：MALFORMED_TOKEN / UNSUPPORTED_ALGORITHM / UNKNOWN_KEY / INVALID_SIGNATURE / TOKEN_EXPIRED / TOKEN_NOT_YET_VALID")
        String failureCode,
        @Schema(description = "校验失败说明") String failureMessage
    ) {
    }
}
