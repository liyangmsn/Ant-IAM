package com.antiam.web;

import static com.antiam.dto.OAuthDtos.AuthorizationResponse;
import static com.antiam.dto.OAuthDtos.ConsentResponse;
import static com.antiam.dto.OAuthDtos.GrantConsentRequest;
import static com.antiam.dto.OAuthDtos.OAuthTokenResponse;
import static com.antiam.dto.OAuthDtos.OidcDiscoveryResponse;
import static com.antiam.dto.OAuthDtos.RevokeTokenRequest;
import static com.antiam.dto.OAuthDtos.RevokeTokenResponse;
import static com.antiam.dto.OAuthDtos.TokenIntrospectionResponse;
import static com.antiam.dto.OAuthDtos.TokenResponse;
import static com.antiam.dto.OAuthDtos.UserInfoResponse;

import com.antiam.service.OAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "OAuth 与 OIDC", description = "OAuth2/OIDC 协议端点、用户授权和 token 管理接口")
public class OAuthController {

    private final OAuthService oauth;

    /**
     * 输出 OIDC Discovery 文档。
     */
    @Operation(summary = "OIDC Discovery", description = "返回 OpenID Connect Provider 元数据。")
    @GetMapping("/.well-known/openid-configuration")
    OidcDiscoveryResponse discovery(HttpServletRequest request) {
        return oauth.discovery(request.getRequestURL().toString().replace("/.well-known/openid-configuration", ""));
    }

    /**
     * OAuth2 授权端点，生成授权码或授权响应。
     */
    @Operation(summary = "OAuth2 授权", description = "处理授权请求，支持授权码流程和 PKCE 参数。")
    @GetMapping("/oauth2/authorize")
    AuthorizationResponse authorize(
        @Parameter(description = "响应类型，例如 code") @RequestParam("response_type") String responseType,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam("client_id") String clientId,
        @Parameter(description = "授权完成后的回调地址") @RequestParam("redirect_uri") String redirectUri,
        @Parameter(description = "请求授权范围，多个 scope 使用空格分隔") @RequestParam(required = false) String scope,
        @Parameter(description = "PKCE code challenge") @RequestParam(value = "code_challenge", required = false) String codeChallenge,
        @Parameter(description = "PKCE code challenge 方法") @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
        @Parameter(description = "客户端透传状态") @RequestParam(required = false) String state,
        Principal principal
    ) {
        return oauth.authorize(responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, principal.getName());
    }

    /**
     * OAuth2 token 端点，签发访问令牌和刷新令牌。
     */
    @Operation(summary = "OAuth2 Token", description = "通过授权码或刷新令牌换取 access_token、refresh_token 和 id_token。")
    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenResponse token(
        @Parameter(description = "授权类型，例如 authorization_code 或 refresh_token") @RequestParam("grant_type") String grantType,
        @Parameter(description = "授权码") @RequestParam(required = false) String code,
        @Parameter(description = "授权请求中的回调地址") @RequestParam(value = "redirect_uri", required = false) String redirectUri,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam("client_id") String clientId,
        @Parameter(description = "OAuth 客户端密钥") @RequestParam("client_secret") String clientSecret,
        @Parameter(description = "PKCE code verifier") @RequestParam(value = "code_verifier", required = false) String codeVerifier,
        @Parameter(description = "刷新令牌") @RequestParam(value = "refresh_token", required = false) String refreshToken,
        HttpServletRequest request
    ) {
        return oauth.token(grantType, code, redirectUri, clientId, clientSecret, codeVerifier, refreshToken, issuer(request));
    }

    /**
     * 查询 OAuth 授权同意记录。
     */
    @Operation(summary = "查询授权同意", description = "查询用户对 OAuth 客户端的 scope 授权同意记录。")
    @GetMapping("/oauth2/consents")
    List<ConsentResponse> consents(
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam(required = false) String clientId,
        @Parameter(description = "是否仅查询有效授权") @RequestParam(required = false) Boolean active,
        @Parameter(description = "关键字，匹配用户、客户端或授权范围") @RequestParam(required = false) String keyword,
        @Parameter(description = "返回数量上限") @RequestParam(required = false) Integer limit,
        Principal principal
    ) {
        return oauth.listConsents(principal.getName(), userId, clientId, active, keyword, limit);
    }

    /**
     * 主动授予 OAuth 授权同意。
     */
    @Operation(summary = "授予授权同意", description = "为用户和客户端记录授权同意范围。")
    @PostMapping("/oauth2/consents")
    ConsentResponse grantConsent(
        @Parameter(description = "授权同意请求") @RequestBody GrantConsentRequest request,
        Principal principal
    ) {
        return oauth.grantConsent(request, principal.getName());
    }

    /**
     * 查询单个授权同意详情。
     */
    @Operation(summary = "获取授权同意详情", description = "根据授权同意 UUID 返回详情。")
    @GetMapping("/oauth2/consents/{consentId}")
    ConsentResponse consent(@Parameter(description = "授权同意 UUID") @PathVariable UUID consentId) {
        return oauth.getConsent(consentId);
    }

    /**
     * 撤销授权同意记录。
     */
    @Operation(summary = "撤销授权同意", description = "撤销用户对客户端的授权同意。")
    @PostMapping("/oauth2/consents/{consentId}/revoke")
    ConsentResponse revokeConsent(@Parameter(description = "授权同意 UUID") @PathVariable UUID consentId, Principal principal) {
        return oauth.revokeConsent(consentId, principal.getName());
    }

    /**
     * OIDC UserInfo 端点。
     */
    @Operation(summary = "OIDC UserInfo", description = "根据 Bearer access_token 返回当前用户声明。")
    @GetMapping("/oauth2/userinfo")
    UserInfoResponse userInfo(@Parameter(description = "Bearer access_token") @RequestHeader("Authorization") String authorizationHeader) {
        return oauth.userInfo(authorizationHeader);
    }

    /**
     * 通过管理接口撤销刷新令牌。
     */
    @Operation(summary = "撤销刷新令牌", description = "根据请求体撤销指定刷新令牌。")
    @PostMapping("/oauth2/revocations")
    RevokeTokenResponse revokeRefreshToken(
        @Parameter(description = "刷新令牌撤销请求") @Valid @RequestBody RevokeTokenRequest request,
        Principal principal
    ) {
        return oauth.revokeRefreshToken(request, principal.getName());
    }

    /**
     * OAuth2 Token Introspection 端点。
     */
    @Operation(summary = "Token Introspection", description = "校验 token 是否有效，并返回客户端和用户等元信息。")
    @PostMapping(value = "/oauth2/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    TokenIntrospectionResponse introspect(
        @Parameter(description = "待校验 token") @RequestParam String token,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam("client_id") String clientId,
        @Parameter(description = "OAuth 客户端密钥") @RequestParam("client_secret") String clientSecret
    ) {
        return oauth.introspect(token, clientId, clientSecret);
    }

    /**
     * OAuth2 Token Revocation 端点。
     */
    @Operation(summary = "Token Revocation", description = "按 OAuth2 Revocation 规范撤销 access_token 或 refresh_token。")
    @PostMapping(value = "/oauth2/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    RevokeTokenResponse revokeToken(
        @Parameter(description = "待撤销 token") @RequestParam String token,
        @Parameter(description = "token 类型提示，例如 access_token 或 refresh_token") @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam("client_id") String clientId,
        @Parameter(description = "OAuth 客户端密钥") @RequestParam("client_secret") String clientSecret
    ) {
        return oauth.revokeToken(token, tokenTypeHint, clientId, clientSecret);
    }

    /**
     * 查询服务端已签发的 OAuth token。
     */
    @Operation(summary = "查询 OAuth Token", description = "管理端查询 access_token 和 refresh_token 元数据，可按类型、用户、客户端和有效状态过滤。")
    @GetMapping("/api/v1/oauth/tokens")
    List<OAuthTokenResponse> tokens(
        @Parameter(description = "token 类型，例如 access 或 refresh") @RequestParam(required = false) String tokenType,
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "OAuth 客户端 ID") @RequestParam(required = false) String clientId,
        @Parameter(description = "是否仅查询有效 token") @RequestParam(required = false) Boolean active,
        @Parameter(description = "关键字，匹配用户、客户端或 scope") @RequestParam(required = false) String keyword,
        @Parameter(description = "返回数量上限") @RequestParam(required = false) Integer limit
    ) {
        return oauth.listTokens(tokenType, userId, clientId, active, keyword, limit);
    }

    /**
     * 查询单个 OAuth token 元数据。
     */
    @Operation(summary = "获取 OAuth Token 详情", description = "根据 token 类型和 UUID 查询 token 元数据。")
    @GetMapping("/api/v1/oauth/tokens/{tokenType}/{tokenId}")
    OAuthTokenResponse token(
        @Parameter(description = "token 类型，例如 access 或 refresh") @PathVariable String tokenType,
        @Parameter(description = "token UUID") @PathVariable UUID tokenId
    ) {
        return oauth.getToken(tokenType, tokenId);
    }

    /**
     * 管理端撤销已存储 token。
     */
    @Operation(summary = "撤销已存储 Token", description = "管理端根据 token 类型和 UUID 撤销 token。")
    @PostMapping("/api/v1/oauth/tokens/{tokenType}/{tokenId}/revoke")
    OAuthTokenResponse revokeStoredToken(
        @Parameter(description = "token 类型，例如 access 或 refresh") @PathVariable String tokenType,
        @Parameter(description = "token UUID") @PathVariable UUID tokenId,
        Principal principal
    ) {
        return oauth.revokeStoredToken(tokenType, tokenId, principal.getName());
    }

    private String issuer(HttpServletRequest request) {
        String url = request.getRequestURL().toString();
        String path = request.getRequestURI();
        return url.substring(0, url.length() - path.length());
    }
}
