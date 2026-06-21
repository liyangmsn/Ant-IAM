package com.antiam.service;

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

import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationSsoConfig;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.OAuthAccessToken;
import com.antiam.domain.OAuthAuthorizationCode;
import com.antiam.domain.OAuthConsent;
import com.antiam.domain.OAuthRefreshToken;
import com.antiam.domain.UserAccount;
import com.antiam.repository.ApplicationSsoConfigRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthAuthorizationCodeRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final ApplicationSsoConfigRepository ssoConfigs;
    private final OAuthAuthorizationCodeRepository authorizationCodes;
    private final OAuthAccessTokenRepository accessTokens;
    private final OAuthRefreshTokenRepository refreshTokens;
    private final OAuthConsentRepository consents;
    private final UserAccountRepository users;
    private final AuthenticationEventRepository authenticationEvents;
    private final TokenSupport tokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    // 处理 OAuth2 授权请求，校验客户端、回调地址、scope 和 PKCE，必要时要求用户同意。
    public AuthorizationResponse authorize(
        String responseType,
        String clientId,
        String redirectUri,
        String scope,
        String state,
        String codeChallenge,
        String codeChallengeMethod,
        String username
    ) {
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("Only authorization code flow is supported");
        }
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateRedirectUri(config, redirectUri);
        validatePkceChallenge(codeChallenge, codeChallengeMethod);
        Set<String> approvedScopes = validateScopes(config, scope);
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        if (!hasConsent(clientId, user, approvedScopes)) {
            return new AuthorizationResponse(consentRedirect(clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod), null, state, true, List.copyOf(approvedScopes));
        }

        String code = tokens.generateToken(32);
        OAuthAuthorizationCode saved = authorizationCodes.save(new OAuthAuthorizationCode(
            tokens.sha256(code),
            config.getApplication(),
            user,
            clientId,
            redirectUri,
            joinScopes(approvedScopes),
            state,
            codeChallenge,
            normalizeCodeChallengeMethod(codeChallengeMethod),
            Instant.now().plus(CODE_TTL)));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            config.getApplication(),
            AuthenticationEventType.LOGIN_SUCCESS,
            null,
            null,
            "authorization_code=" + saved.getId()));
        return new AuthorizationResponse(buildRedirect(redirectUri, code, state), code, state, false, List.copyOf(approvedScopes));
    }

    @Transactional
    // 记录或更新用户对客户端的授权同意范围。
    public ConsentResponse grantConsent(GrantConsentRequest request, String username) {
        ApplicationSsoConfig config = getOauthClient(request.clientId());
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        String requestedScope = request.scopes() == null ? "" : String.join(" ", request.scopes());
        Set<String> approvedScopes = validateScopes(config, requestedScope);
        OAuthConsent saved = consents.findByClientIdAndUserId(request.clientId(), user.getId())
            .map(existing -> {
                existing.replaceScopes(joinScopes(approvedScopes));
                return existing;
            })
            .orElseGet(() -> consents.save(new OAuthConsent(
                config.getApplication(),
                user,
                request.clientId(),
                joinScopes(approvedScopes))));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            config.getApplication(),
            AuthenticationEventType.CONSENT_GRANTED,
            null,
            null,
            "consent_granted;client_id=" + request.clientId()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询 OAuth 授权同意记录，默认限制在当前用户视角。
    public List<ConsentResponse> listConsents(
        String username,
        UUID userId,
        String clientId,
        Boolean active,
        String keyword,
        Integer limit
    ) {
        UUID effectiveUserId = userId == null ? currentUser(username).getId() : userId;
        String normalizedClientId = normalizeKeyword(clientId);
        String normalizedKeyword = normalizeKeyword(keyword);
        int cappedLimit = limit == null ? 100 : Math.clamp(limit, 1, 500);
        return consents.findAll(Sort.by(Sort.Direction.DESC, "grantedAt")).stream()
            .filter(consent -> consent.getUser().getId().equals(effectiveUserId))
            .filter(consent -> normalizedClientId == null || contains(consent.getClientId(), normalizedClientId))
            .filter(consent -> active == null || consent.isActive() == active)
            .filter(consent -> normalizedKeyword == null || matchesConsentKeyword(consent, normalizedKeyword))
            .limit(cappedLimit)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询单条 OAuth 授权同意详情。
    public ConsentResponse getConsent(UUID consentId) {
        return consents.findById(consentId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("OAuth consent not found: " + consentId));
    }

    @Transactional
    // 撤销用户授权同意，使后续授权流程需要重新确认 scope。
    public ConsentResponse revokeConsent(UUID consentId, String actor) {
        OAuthConsent consent = consents.findById(consentId)
            .orElseThrow(() -> new NotFoundException("OAuth consent not found: " + consentId));
        consent.revoke();
        authenticationEvents.save(new AuthenticationEvent(
            null,
            consent.getUser(),
            consent.getApplication(),
            AuthenticationEventType.CONSENT_REVOKED,
            null,
            null,
            "consent_revoked;client_id=" + consent.getClientId()));
        auditService.record(actor, "oauth_consent.revoke", "oauth_consent", consentId.toString(), consent.getClientId());
        return toResponse(consent);
    }

    @Transactional
    // OAuth2 token 入口，按 grant_type 分发授权码换 token 或刷新 token。
    public TokenResponse token(
        String grantType,
        String code,
        String redirectUri,
        String clientId,
        String clientSecret,
        String codeVerifier,
        String refreshToken,
        String issuer
    ) {
        if ("authorization_code".equals(grantType)) {
            return exchangeAuthorizationCode(code, redirectUri, clientId, clientSecret, codeVerifier, issuer);
        }
        if ("refresh_token".equals(grantType)) {
            return refreshAccessToken(refreshToken, clientId, clientSecret, issuer);
        }
        throw new IllegalArgumentException("Unsupported grant type: " + grantType);
    }

    private TokenResponse exchangeAuthorizationCode(
        String code,
        String redirectUri,
        String clientId,
        String clientSecret,
        String codeVerifier,
        String issuer
    ) {
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateClientSecret(config, clientSecret);
        OAuthAuthorizationCode authorizationCode = authorizationCodes.findByCodeHash(tokens.sha256(code))
            .orElseThrow(() -> new NotFoundException("Authorization code not found"));
        if (!authorizationCode.isUsable(Instant.now())) {
            throw new IllegalArgumentException("Authorization code is expired or already consumed");
        }
        if (!authorizationCode.getClientId().equals(clientId) || !authorizationCode.getRedirectUri().equals(redirectUri)) {
            throw new IllegalArgumentException("Authorization code binding does not match client request");
        }
        validatePkceVerifier(authorizationCode, codeVerifier);
        authorizationCode.consume();

        String refreshToken = tokens.generateToken(48);
        refreshTokens.save(new OAuthRefreshToken(
            tokens.sha256(refreshToken),
            authorizationCode.getApplication(),
            authorizationCode.getUser(),
            clientId,
            authorizationCode.getScopes(),
            Instant.now().plus(REFRESH_TOKEN_TTL)));
        TokenResponse response = issueAccessToken(
            config,
            authorizationCode.getUser(),
            clientId,
            authorizationCode.getScopes(),
            refreshToken,
            issuer);
        authenticationEvents.save(new AuthenticationEvent(
            null,
            authorizationCode.getUser(),
            authorizationCode.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "client_id=" + clientId + ";grant_type=authorization_code"));
        return response;
    }

    private TokenResponse refreshAccessToken(String refreshToken, String clientId, String clientSecret, String issuer) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("refresh_token is required");
        }
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateClientSecret(config, clientSecret);
        OAuthRefreshToken storedRefreshToken = refreshTokens.findByTokenHash(tokens.sha256(refreshToken))
            .orElseThrow(() -> new NotFoundException("Refresh token not found"));
        if (!storedRefreshToken.isActive(Instant.now())) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }
        if (!storedRefreshToken.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Refresh token binding does not match client request");
        }
        storedRefreshToken.markUsed();
        storedRefreshToken.revoke();
        String rotatedRefreshToken = tokens.generateToken(48);
        refreshTokens.save(new OAuthRefreshToken(
            tokens.sha256(rotatedRefreshToken),
            storedRefreshToken.getApplication(),
            storedRefreshToken.getUser(),
            clientId,
            storedRefreshToken.getScopes(),
            Instant.now().plus(REFRESH_TOKEN_TTL)));
        TokenResponse response = issueAccessToken(
            config,
            storedRefreshToken.getUser(),
            clientId,
            storedRefreshToken.getScopes(),
            rotatedRefreshToken,
            issuer);
        authenticationEvents.save(new AuthenticationEvent(
            null,
            storedRefreshToken.getUser(),
            storedRefreshToken.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "client_id=" + clientId + ";grant_type=refresh_token"));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            storedRefreshToken.getUser(),
            storedRefreshToken.getApplication(),
            AuthenticationEventType.TOKEN_REVOKED,
            null,
            null,
            "client_id=" + clientId + ";token_type=refresh_token;reason=rotation"));
        return response;
    }

    @Transactional
    // 当前用户撤销自己的刷新令牌。
    public RevokeTokenResponse revokeRefreshToken(RevokeTokenRequest request, String username) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        OAuthRefreshToken storedRefreshToken = refreshTokens.findByTokenHash(tokens.sha256(request.refreshToken()))
            .orElseThrow(() -> new NotFoundException("Refresh token not found"));
        if (!storedRefreshToken.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Refresh token does not belong to current user");
        }
        if (storedRefreshToken.getRevokedAt() == null) {
            storedRefreshToken.revoke();
            authenticationEvents.save(new AuthenticationEvent(
                null,
                storedRefreshToken.getUser(),
                storedRefreshToken.getApplication(),
                AuthenticationEventType.TOKEN_REVOKED,
                null,
                null,
                "client_id=" + storedRefreshToken.getClientId() + ";token_type=refresh_token"));
        }
        return new RevokeTokenResponse(true);
    }

    @Transactional(readOnly = true)
    // 校验访问令牌是否有效，并按 introspection 响应格式返回元信息。
    public TokenIntrospectionResponse introspect(String token, String clientId, String clientSecret) {
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateClientSecret(config, clientSecret);
        return accessTokens.findByTokenHash(tokens.sha256(requiredToken(token)))
            .filter(accessToken -> accessToken.getClientId().equals(clientId))
            .map(this::toIntrospectionResponse)
            .orElseGet(() -> new TokenIntrospectionResponse(false, null, null, null, null, null, 0));
    }

    @Transactional
    // 按 OAuth2 Revocation 规范撤销访问令牌或刷新令牌。
    public RevokeTokenResponse revokeToken(String token, String tokenTypeHint, String clientId, String clientSecret) {
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateClientSecret(config, clientSecret);
        String tokenHash = tokens.sha256(requiredToken(token));
        boolean revoked = false;
        if (tokenTypeHint == null || tokenTypeHint.isBlank() || "access_token".equals(tokenTypeHint)) {
            revoked = accessTokens.findByTokenHash(tokenHash)
                .filter(accessToken -> accessToken.getClientId().equals(clientId))
                .map(accessToken -> {
                    accessToken.revoke();
                    authenticationEvents.save(new AuthenticationEvent(
                        null,
                        accessToken.getUser(),
                        accessToken.getApplication(),
                        AuthenticationEventType.TOKEN_REVOKED,
                        null,
                        null,
                        "client_id=" + clientId + ";token_type=access_token"));
                    return true;
                })
                .orElse(false);
        }
        if (!revoked && (tokenTypeHint == null || tokenTypeHint.isBlank() || "refresh_token".equals(tokenTypeHint))) {
            revoked = refreshTokens.findByTokenHash(tokenHash)
                .filter(refreshToken -> refreshToken.getClientId().equals(clientId))
                .map(refreshToken -> {
                    refreshToken.revoke();
                    authenticationEvents.save(new AuthenticationEvent(
                        null,
                        refreshToken.getUser(),
                        refreshToken.getApplication(),
                        AuthenticationEventType.TOKEN_REVOKED,
                        null,
                        null,
                        "client_id=" + clientId + ";token_type=refresh_token"));
                    return true;
                })
                .orElse(false);
        }
        return new RevokeTokenResponse(revoked);
    }

    @Transactional(readOnly = true)
    // 管理端查询已签发 token 的元数据，支持类型、用户、客户端、活跃状态和关键字过滤。
    public List<OAuthTokenResponse> listTokens(
        String tokenType,
        UUID userId,
        String clientId,
        Boolean active,
        String keyword,
        Integer limit
    ) {
        String normalizedType = normalizeTokenType(tokenType);
        String normalizedClientId = normalizeKeyword(clientId);
        String normalizedKeyword = normalizeKeyword(keyword);
        Instant now = Instant.now();
        int cappedLimit = limit == null ? 100 : Math.clamp(limit, 1, 500);
        Stream<OAuthTokenResponse> values = switch (normalizedType) {
            case "access" -> accessTokens.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(token -> toResponse(token, now));
            case "refresh" -> refreshTokens.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(token -> toResponse(token, now));
            default -> Stream.concat(
                accessTokens.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(token -> toResponse(token, now)),
                refreshTokens.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(token -> toResponse(token, now)));
        };
        return values
            .filter(token -> userId == null || token.userId().equals(userId))
            .filter(token -> normalizedClientId == null || contains(token.clientId(), normalizedClientId))
            .filter(token -> active == null || token.active() == active)
            .filter(token -> normalizedKeyword == null || matchesTokenKeyword(token, normalizedKeyword))
            .limit(cappedLimit)
            .toList();
    }

    @Transactional(readOnly = true)
    // 管理端查询单个 access token 或 refresh token 的元数据。
    public OAuthTokenResponse getToken(String tokenType, UUID tokenId) {
        Instant now = Instant.now();
        return switch (requiredTokenType(tokenType)) {
            case "access" -> accessTokens.findById(tokenId)
                .map(token -> toResponse(token, now))
                .orElseThrow(() -> new NotFoundException("OAuth access token not found: " + tokenId));
            case "refresh" -> refreshTokens.findById(tokenId)
                .map(token -> toResponse(token, now))
                .orElseThrow(() -> new NotFoundException("OAuth refresh token not found: " + tokenId));
            default -> throw new IllegalArgumentException("Unsupported token type: " + tokenType);
        };
    }

    @Transactional
    // 管理端按 token 类型和 ID 撤销已存储 token。
    public OAuthTokenResponse revokeStoredToken(String tokenType, UUID tokenId, String actor) {
        Instant now = Instant.now();
        return switch (requiredTokenType(tokenType)) {
            case "access" -> {
                OAuthAccessToken token = accessTokens.findById(tokenId)
                    .orElseThrow(() -> new NotFoundException("OAuth access token not found: " + tokenId));
                token.revoke();
                recordTokenRevoked(token.getUser(), token.getApplication(), token.getClientId(), "access_token");
                auditService.record(actor, "oauth_token.revoke", "oauth_access_token", tokenId.toString(), token.getClientId());
                yield toResponse(token, now);
            }
            case "refresh" -> {
                OAuthRefreshToken token = refreshTokens.findById(tokenId)
                    .orElseThrow(() -> new NotFoundException("OAuth refresh token not found: " + tokenId));
                token.revoke();
                recordTokenRevoked(token.getUser(), token.getApplication(), token.getClientId(), "refresh_token");
                auditService.record(actor, "oauth_token.revoke", "oauth_refresh_token", tokenId.toString(), token.getClientId());
                yield toResponse(token, now);
            }
            default -> throw new IllegalArgumentException("Unsupported token type: " + tokenType);
        };
    }

    private TokenResponse issueAccessToken(
        ApplicationSsoConfig config,
        UserAccount user,
        String clientId,
        String scopes,
        String refreshToken,
        String issuer
    ) {
        String accessToken = tokens.generateToken(48);
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        String idToken = jwtService.signIdToken(
            issuer,
            user,
            clientId,
            scopes,
            expiresAt,
            splitValues(config.getIdTokenClaims()),
            splitEntries(config.getCustomClaims()));
        accessTokens.save(new OAuthAccessToken(
            tokens.sha256(accessToken),
            tokens.sha256(idToken),
            config.getApplication(),
            user,
            clientId,
            scopes,
            expiresAt));
        return new TokenResponse(accessToken, "Bearer", TOKEN_TTL.toSeconds(), refreshToken, idToken, scopes);
    }

    @Transactional(readOnly = true)
    // 根据 Bearer access token 返回 OIDC UserInfo 声明。
    public UserInfoResponse userInfo(String authorizationHeader) {
        String bearerToken = extractBearerToken(authorizationHeader);
        OAuthAccessToken token = accessTokens.findByTokenHash(tokens.sha256(bearerToken))
            .orElseThrow(() -> new NotFoundException("Access token not found"));
        if (!token.isActive(Instant.now())) {
            throw new IllegalArgumentException("Access token is expired or revoked");
        }
        UserAccount user = token.getUser();
        return new UserInfoResponse(
            user.getId().toString(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getMobile());
    }

    // 生成 OIDC Provider Discovery 元数据。
    public OidcDiscoveryResponse discovery(String issuer) {
        String normalized = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return new OidcDiscoveryResponse(
            normalized,
            normalized + "/oauth2/authorize",
            normalized + "/oauth2/token",
            normalized + "/oauth2/userinfo",
            normalized + "/oauth2/introspect",
            normalized + "/oauth2/revoke",
            normalized + "/oauth2/jwks",
            List.of("code"),
            List.of("authorization_code", "refresh_token"),
            List.of("public"),
            List.of("RS256"),
            List.of("openid", "profile", "email", "phone", "offline_access"),
            List.of("S256", "plain"),
            List.of("client_secret_post"),
            Map.of("standard", List.of("sub", "preferred_username", "name", "email", "phone_number")));
    }

    private ApplicationSsoConfig getOauthClient(String clientId) {
        ApplicationSsoConfig config = ssoConfigs.findByClientId(clientId)
            .orElseThrow(() -> new NotFoundException("OAuth client not found: " + clientId));
        if (!config.isEnabled()) {
            throw new IllegalArgumentException("OAuth client is disabled");
        }
        if (!config.getApplication().isEnabled()) {
            throw new IllegalArgumentException("Application is disabled");
        }
        if (config.getProtocol() != ApplicationProtocol.OIDC && config.getProtocol() != ApplicationProtocol.OAUTH2) {
            throw new IllegalArgumentException("Application is not configured for OAuth2/OIDC");
        }
        return config;
    }

    private void validateRedirectUri(ApplicationSsoConfig config, String redirectUri) {
        if (!splitValues(config.getRedirectUris()).contains(redirectUri)) {
            throw new IllegalArgumentException("redirect_uri is not registered for client");
        }
    }

    private Set<String> validateScopes(ApplicationSsoConfig config, String requestedScope) {
        Set<String> registeredScopes = splitValues(config.getScopes());
        Set<String> requestedScopes = splitScopes(requestedScope);
        if (requestedScopes.isEmpty()) {
            return registeredScopes;
        }
        if (!registeredScopes.containsAll(requestedScopes)) {
            throw new IllegalArgumentException("Requested scope exceeds registered scopes");
        }
        return requestedScopes;
    }

    private boolean hasConsent(String clientId, UserAccount user, Set<String> requestedScopes) {
        return consents.findByClientIdAndUserId(clientId, user.getId())
            .filter(OAuthConsent::isActive)
            .map(consent -> splitScopes(consent.getScopes()).containsAll(requestedScopes))
            .orElse(false);
    }

    private ConsentResponse toResponse(OAuthConsent consent) {
        return new ConsentResponse(
            consent.getId(),
            consent.getUser().getId(),
            consent.getUser().getUsername(),
            consent.getClientId(),
            consent.getApplication().getCode(),
            List.copyOf(splitScopes(consent.getScopes())),
            consent.getGrantedAt(),
            consent.getRevokedAt(),
            consent.isActive());
    }

    private OAuthTokenResponse toResponse(OAuthAccessToken token, Instant now) {
        return new OAuthTokenResponse(
            token.getId(),
            "access",
            token.getUser().getId(),
            token.getUser().getUsername(),
            token.getApplication().getId(),
            token.getApplication().getCode(),
            token.getClientId(),
            List.copyOf(splitScopes(token.getScopes())),
            token.getCreatedAt(),
            token.getExpiresAt(),
            token.getRevokedAt(),
            null,
            token.isActive(now));
    }

    private OAuthTokenResponse toResponse(OAuthRefreshToken token, Instant now) {
        return new OAuthTokenResponse(
            token.getId(),
            "refresh",
            token.getUser().getId(),
            token.getUser().getUsername(),
            token.getApplication().getId(),
            token.getApplication().getCode(),
            token.getClientId(),
            List.copyOf(splitScopes(token.getScopes())),
            token.getCreatedAt(),
            token.getExpiresAt(),
            token.getRevokedAt(),
            token.getLastUsedAt(),
            token.isActive(now));
    }

    private UserAccount currentUser(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesConsentKeyword(OAuthConsent consent, String keyword) {
        return contains(consent.getClientId(), keyword)
            || contains(consent.getApplication().getCode(), keyword)
            || contains(consent.getApplication().getName(), keyword)
            || contains(consent.getUser().getUsername(), keyword)
            || contains(consent.getUser().getDisplayName(), keyword)
            || contains(consent.getScopes(), keyword);
    }

    private boolean matchesTokenKeyword(OAuthTokenResponse token, String keyword) {
        return contains(token.tokenType(), keyword)
            || contains(token.username(), keyword)
            || contains(token.applicationCode(), keyword)
            || contains(token.clientId(), keyword)
            || token.scopes().stream().anyMatch(scope -> contains(scope, keyword));
    }

    private String normalizeTokenType(String tokenType) {
        if (tokenType == null || tokenType.isBlank() || "all".equalsIgnoreCase(tokenType)) {
            return "all";
        }
        return requiredTokenType(tokenType);
    }

    private String requiredTokenType(String tokenType) {
        if ("access".equalsIgnoreCase(tokenType) || "access_token".equalsIgnoreCase(tokenType)) {
            return "access";
        }
        if ("refresh".equalsIgnoreCase(tokenType) || "refresh_token".equalsIgnoreCase(tokenType)) {
            return "refresh";
        }
        throw new IllegalArgumentException("tokenType must be access or refresh");
    }

    private void recordTokenRevoked(UserAccount user, com.antiam.domain.Application application, String clientId, String tokenType) {
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            application,
            AuthenticationEventType.TOKEN_REVOKED,
            null,
            null,
            "client_id=" + clientId + ";token_type=" + tokenType + ";reason=admin"));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void validateClientSecret(ApplicationSsoConfig config, String clientSecret) {
        if (config.getClientSecretHash() == null || !passwordEncoder.matches(clientSecret, config.getClientSecretHash())) {
            throw new IllegalArgumentException("Invalid client credentials");
        }
    }

    private void validatePkceChallenge(String codeChallenge, String codeChallengeMethod) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("code_challenge is required");
        }
        String method = normalizeCodeChallengeMethod(codeChallengeMethod);
        if (!"S256".equals(method) && !"plain".equals(method)) {
            throw new IllegalArgumentException("Unsupported code_challenge_method: " + method);
        }
    }

    private void validatePkceVerifier(OAuthAuthorizationCode authorizationCode, String codeVerifier) {
        if (authorizationCode.getCodeChallenge() == null || authorizationCode.getCodeChallenge().isBlank()) {
            return;
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("code_verifier is required");
        }
        String expected = "S256".equals(authorizationCode.getCodeChallengeMethod())
            ? s256(codeVerifier)
            : codeVerifier;
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            authorizationCode.getCodeChallenge().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid code_verifier");
        }
    }

    private String normalizeCodeChallengeMethod(String codeChallengeMethod) {
        if (codeChallengeMethod == null || codeChallengeMethod.isBlank()) {
            return "plain";
        }
        return codeChallengeMethod;
    }

    private String s256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer access token is required");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    private String buildRedirect(String redirectUri, String code, String state) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        String url = redirectUri + separator + "code=" + urlEncode(code);
        if (state != null && !state.isBlank()) {
            url += "&state=" + urlEncode(state);
        }
        return url;
    }

    private String consentRedirect(String clientId, String redirectUri, String scope, String state, String codeChallenge, String codeChallengeMethod) {
        StringBuilder target = new StringBuilder("/oauth2/consent?client_id=")
            .append(urlEncode(clientId))
            .append("&redirect_uri=")
            .append(urlEncode(redirectUri));
        if (scope != null && !scope.isBlank()) {
            target.append("&scope=").append(urlEncode(scope));
        }
        if (state != null && !state.isBlank()) {
            target.append("&state=").append(urlEncode(state));
        }
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            target.append("&code_challenge=").append(urlEncode(codeChallenge));
        }
        if (codeChallengeMethod != null && !codeChallengeMethod.isBlank()) {
            target.append("&code_challenge_method=").append(urlEncode(codeChallengeMethod));
        }
        return target.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Set<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("\\R"))
            .filter(item -> !item.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> splitScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("\\s+"))
            .filter(item -> !item.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String joinScopes(Set<String> scopes) {
        return String.join(" ", scopes);
    }

    private String requiredToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        return token;
    }

    private TokenIntrospectionResponse toIntrospectionResponse(OAuthAccessToken accessToken) {
        boolean active = accessToken.isActive(Instant.now());
        return new TokenIntrospectionResponse(
            active,
            active ? accessToken.getClientId() : null,
            active ? accessToken.getUser().getUsername() : null,
            active ? accessToken.getUser().getId().toString() : null,
            active ? "Bearer" : null,
            active ? accessToken.getScopes() : null,
            active ? accessToken.getExpiresAt().getEpochSecond() : 0);
    }

    private Map<String, String> splitEntries(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(value.split("\\R"))
            .filter(item -> !item.isBlank())
            .map(item -> {
                int separator = item.indexOf('=');
                String key = separator < 0 ? item : item.substring(0, separator);
                String entryValue = separator < 0 ? "" : item.substring(separator + 1);
                return Map.entry(key, entryValue);
            })
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (first, second) -> second,
                java.util.LinkedHashMap::new));
    }
}
