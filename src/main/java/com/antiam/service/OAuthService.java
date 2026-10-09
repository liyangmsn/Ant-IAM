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
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDecisionResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionResponse;
import com.antiam.dto.ApplicationPermissionDtos.PermissionCheckRequest;
import com.antiam.dto.ApplicationPermissionDtos.PermissionCheckResponse;
import com.antiam.dto.ApplicationPermissionDtos.SyncApplicationPermissionsRequest;
import com.antiam.common.OAuthException;
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
import java.util.LinkedHashMap;
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
    private static final Duration TOKEN_TTL = Duration.ofMinutes(20);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final Duration ID_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Set<String> DEFAULT_GRANT_TYPES = Set.of("authorization_code", "refresh_token");

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
    private final AccessService access;
    private final ApplicationPermissionService applicationPermissions;

    @Transactional
    // 处理 OAuth2 授权请求，校验客户端、回调地址、scope 和 PKCE，必要时要求用户同意。
    public AuthorizationResponse authorize(
        String responseType,
        String clientId,
        String redirectUri,
        String scope,
        String state,
        String nonce,
        String codeChallenge,
        String codeChallengeMethod,
        String username
    ) {
        if (!"code".equals(responseType)) {
            throw new OAuthException("unsupported_response_type", "Only authorization code flow is supported");
        }
        ApplicationSsoConfig config = getOauthClient(clientId);
        requireGrantType(config, "authorization_code");
        validateRedirectUri(config, redirectUri);
        validatePkceChallenge(config.isPkceRequired(), codeChallenge, codeChallengeMethod);
        Set<String> approvedScopes = validateScopes(config, scope);
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        access.requireApplicationAccess(config.getApplication(), user);
        if (!hasConsent(clientId, user, approvedScopes)) {
            return new AuthorizationResponse(consentRedirect(clientId, redirectUri, scope, state, nonce, codeChallenge, codeChallengeMethod), null, state, true, List.copyOf(approvedScopes));
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
            nonce,
            codeChallenge,
            normalizeCodeChallengeMethod(codeChallengeMethod),
            Instant.now().plus(ttl(config.getAuthorizationCodeTtlMinutes(), CODE_TTL))));
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
        // 未获得应用访问授权的用户不能预先记录授权同意。
        access.requireApplicationAccess(config.getApplication(), user);
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

    // OAuth2 token 入口，按 grant_type 分发授权码换 token 或刷新 token。
    // 协议错误不回滚事务：授权码一经出示即作废，PKCE 或绑定校验失败后也不能重试。
    @Transactional(noRollbackFor = OAuthException.class)
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
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required");
        }
        ApplicationSsoConfig config = authenticatedClient(clientId, clientSecret);
        if ("authorization_code".equals(grantType)) {
            requireGrantType(config, grantType);
            return exchangeAuthorizationCode(config, code, redirectUri, clientId, codeVerifier, issuer);
        }
        if ("refresh_token".equals(grantType)) {
            requireGrantType(config, grantType);
            return refreshAccessToken(config, refreshToken, clientId, issuer);
        }
        throw new OAuthException("unsupported_grant_type", "Unsupported grant type: " + grantType);
    }

    private TokenResponse exchangeAuthorizationCode(
        ApplicationSsoConfig config,
        String code,
        String redirectUri,
        String clientId,
        String codeVerifier,
        String issuer
    ) {
        if (code == null || code.isBlank()) {
            throw OAuthException.invalidRequest("code is required");
        }
        OAuthAuthorizationCode authorizationCode = authorizationCodes.findByCodeHash(tokens.sha256(code))
            .orElseThrow(() -> OAuthException.invalidGrant("Authorization code is invalid"));
        if (!authorizationCode.isUsable(Instant.now())) {
            throw OAuthException.invalidGrant("Authorization code is expired or already consumed");
        }
        // 授权码只能出示一次：先作废再校验绑定和 PKCE，防止攻击者反复猜测 code_verifier。
        authorizationCode.consume();
        if (!authorizationCode.getClientId().equals(clientId) || !authorizationCode.getRedirectUri().equals(redirectUri)) {
            throw OAuthException.invalidGrant("Authorization code binding does not match client request");
        }
        validatePkceVerifier(authorizationCode, codeVerifier);
        requireUserAccess(config, authorizationCode.getUser());

        String refreshToken = null;
        OAuthRefreshToken storedRefreshToken = null;
        if (allowsGrantType(config, "refresh_token")) {
            refreshToken = tokens.generateToken(48);
            storedRefreshToken = refreshTokens.save(new OAuthRefreshToken(
                tokens.sha256(refreshToken),
                authorizationCode.getApplication(),
                authorizationCode.getUser(),
                clientId,
                authorizationCode.getScopes(),
                Instant.now().plus(ttl(config.getRefreshTokenTtlMinutes(), REFRESH_TOKEN_TTL))));
        }
        TokenResponse response = issueAccessToken(
            config,
            authorizationCode.getUser(),
            clientId,
            authorizationCode.getScopes(),
            refreshToken,
            storedRefreshToken == null ? null : storedRefreshToken.getId(),
            authorizationCode.getNonce(),
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

    private TokenResponse refreshAccessToken(ApplicationSsoConfig config, String refreshToken, String clientId, String issuer) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw OAuthException.invalidRequest("refresh_token is required");
        }
        OAuthRefreshToken storedRefreshToken = refreshTokens.findByTokenHash(tokens.sha256(refreshToken))
            .orElseThrow(() -> OAuthException.invalidGrant("Refresh token is invalid"));
        if (!storedRefreshToken.getClientId().equals(clientId)) {
            throw OAuthException.invalidGrant("Refresh token binding does not match client request");
        }
        if (!storedRefreshToken.isActive(Instant.now())) {
            throw OAuthException.invalidGrant("Refresh token is expired or revoked");
        }
        // 刷新前重新校验用户状态和应用授权，被禁用或取消授权的用户不能继续续期。
        requireUserAccess(config, storedRefreshToken.getUser());
        storedRefreshToken.markUsed();
        // 刷新后旧的 access token 立即失效，避免同一授权并存多个有效访问令牌。
        revokeAccessTokensOf(storedRefreshToken.getId());
        OAuthRefreshToken currentRefreshToken = storedRefreshToken;
        String returnedRefreshToken = refreshToken;
        if (!config.isReuseRefreshTokens()) {
            storedRefreshToken.revoke();
            returnedRefreshToken = tokens.generateToken(48);
            currentRefreshToken = refreshTokens.save(new OAuthRefreshToken(
                tokens.sha256(returnedRefreshToken),
                storedRefreshToken.getApplication(),
                storedRefreshToken.getUser(),
                clientId,
                storedRefreshToken.getScopes(),
                Instant.now().plus(ttl(config.getRefreshTokenTtlMinutes(), REFRESH_TOKEN_TTL))));
            authenticationEvents.save(new AuthenticationEvent(
                null,
                storedRefreshToken.getUser(),
                storedRefreshToken.getApplication(),
                AuthenticationEventType.TOKEN_REVOKED,
                null,
                null,
                "client_id=" + clientId + ";token_type=refresh_token;reason=rotation"));
        }
        TokenResponse response = issueAccessToken(
            config,
            storedRefreshToken.getUser(),
            clientId,
            storedRefreshToken.getScopes(),
            returnedRefreshToken,
            currentRefreshToken.getId(),
            null,
            issuer);
        authenticationEvents.save(new AuthenticationEvent(
            null,
            storedRefreshToken.getUser(),
            storedRefreshToken.getApplication(),
            AuthenticationEventType.TOKEN_ISSUED,
            null,
            null,
            "client_id=" + clientId + ";grant_type=refresh_token"));
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
            revokeAccessTokensOf(storedRefreshToken.getId());
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
    // 按 RFC 7662 校验 access token 或 refresh token，未知或不属于该客户端的 token 一律返回 inactive。
    public TokenIntrospectionResponse introspect(String token, String clientId, String clientSecret) {
        authenticatedClient(clientId, clientSecret);
        String tokenHash = tokens.sha256(requiredToken(token));
        Instant now = Instant.now();
        return accessTokens.findByTokenHash(tokenHash)
            .filter(accessToken -> accessToken.getClientId().equals(clientId))
            .map(accessToken -> toIntrospectionResponse(accessToken, now))
            .or(() -> refreshTokens.findByTokenHash(tokenHash)
                .filter(refreshToken -> refreshToken.getClientId().equals(clientId))
                .map(refreshToken -> toIntrospectionResponse(refreshToken, now)))
            .orElseGet(TokenIntrospectionResponse::inactive);
    }

    @Transactional(readOnly = true)
    // 应用以客户端凭据查询自己已注册的应用内权限点。
    public List<ApplicationPermissionResponse> listClientPermissions(String clientId, String clientSecret) {
        ApplicationSsoConfig config = authenticatedClient(clientId, clientSecret);
        return applicationPermissions.listPermissions(config.getApplication().getId());
    }

    @Transactional
    // 应用以客户端凭据整体同步自己的应用内权限点清单，清单外的权限点会被删除。
    public List<ApplicationPermissionResponse> syncClientPermissions(String clientId, String clientSecret, SyncApplicationPermissionsRequest request) {
        ApplicationSsoConfig config = authenticatedClient(clientId, clientSecret);
        return applicationPermissions.syncPermissions(config.getApplication(), request.permissions(), "client:" + clientId);
    }

    @Transactional(readOnly = true)
    // 应用内鉴权：校验 token 属于该客户端且仍有效，再按应用访问决策和应用内角色判断每个权限点。
    public PermissionCheckResponse checkPermissions(String clientId, String clientSecret, PermissionCheckRequest request) {
        authenticatedClient(clientId, clientSecret);
        Instant now = Instant.now();
        Map<String, Boolean> denied = new LinkedHashMap<>();
        request.permissions().forEach(code -> denied.put(code, false));
        OAuthAccessToken accessToken = accessTokens.findByTokenHash(tokens.sha256(requiredToken(request.token())))
            .filter(token -> token.getClientId().equals(clientId))
            .filter(token -> token.isActive(now) && isGrantHolderActive(token.getApplication(), token.getUser()))
            .orElse(null);
        if (accessToken == null) {
            return new PermissionCheckResponse(false, false, null, null, "token_inactive", denied);
        }
        UserAccount user = accessToken.getUser();
        ApplicationPermissionDecisionResponse decision = access.decideApplicationPermissions(accessToken.getApplication().getId(), user.getId());
        if (!decision.accessAllowed()) {
            return new PermissionCheckResponse(true, false, user.getId().toString(), user.getUsername(), decision.reason(), denied);
        }
        Set<String> granted = Set.copyOf(decision.permissions());
        Map<String, Boolean> results = new LinkedHashMap<>();
        request.permissions().forEach(code -> results.put(code, granted.contains(code)));
        boolean allowed = results.values().stream().allMatch(Boolean::booleanValue);
        return new PermissionCheckResponse(true, allowed, user.getId().toString(), user.getUsername(), allowed ? null : "permission_denied", results);
    }

    /**
     * 业务应用代表用户调用管理接口时，确认客户端身份并解析操作人：用户令牌必须由该客户端签发且仍然有效。
     */
    @Transactional(readOnly = true)
    public ActingUser resolveActingUser(String clientId, String clientSecret, String userToken) {
        ApplicationSsoConfig config = authenticatedClient(clientId, clientSecret);
        if (userToken == null || userToken.isBlank()) {
            throw OAuthException.invalidRequest("X-Acting-User-Token is required");
        }
        Instant now = Instant.now();
        OAuthAccessToken accessToken = accessTokens.findByTokenHash(tokens.sha256(userToken))
            .filter(token -> token.getClientId().equals(clientId))
            .filter(token -> token.isActive(now) && isGrantHolderActive(token.getApplication(), token.getUser()))
            .orElseThrow(() -> OAuthException.invalidToken("Acting user token is invalid, expired or not issued to this client"));
        return new ActingUser(config.getApplication().getId(), accessToken.getUser().getId(), accessToken.getUser().getUsername());
    }

    /** 代表用户调用时解析出的应用与操作人。 */
    public record ActingUser(UUID applicationId, UUID userId, String username) {
    }

    @Transactional
    // 按 RFC 7009 撤销 access token 或 refresh token；token_type_hint 只影响查找顺序，撤销 refresh token 时级联撤销其 access token。
    public RevokeTokenResponse revokeToken(String token, String tokenTypeHint, String clientId, String clientSecret) {
        authenticatedClient(clientId, clientSecret);
        String tokenHash = tokens.sha256(requiredToken(token));
        boolean refreshFirst = "refresh_token".equals(tokenTypeHint);
        boolean revoked = refreshFirst
            ? revokeRefreshByHash(tokenHash, clientId) || revokeAccessByHash(tokenHash, clientId)
            : revokeAccessByHash(tokenHash, clientId) || revokeRefreshByHash(tokenHash, clientId);
        return new RevokeTokenResponse(revoked);
    }

    private boolean revokeAccessByHash(String tokenHash, String clientId) {
        return accessTokens.findByTokenHash(tokenHash)
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

    private boolean revokeRefreshByHash(String tokenHash, String clientId) {
        return refreshTokens.findByTokenHash(tokenHash)
            .filter(refreshToken -> refreshToken.getClientId().equals(clientId))
            .map(refreshToken -> {
                refreshToken.revoke();
                revokeAccessTokensOf(refreshToken.getId());
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

    private void revokeAccessTokensOf(UUID refreshTokenId) {
        accessTokens.findByRefreshTokenIdAndRevokedAtIsNull(refreshTokenId).forEach(OAuthAccessToken::revoke);
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
                revokeAccessTokensOf(token.getId());
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
        UUID refreshTokenId,
        String nonce,
        String issuer
    ) {
        String accessToken = tokens.generateToken(48);
        Duration accessTokenTtl = ttl(config.getAccessTokenTtlMinutes(), TOKEN_TTL);
        Instant now = Instant.now();
        // 只有 OIDC 应用且授权范围包含 openid 时才签发 ID Token。
        String idToken = config.getProtocol() == ApplicationProtocol.OIDC && splitScopes(scopes).contains("openid")
            ? jwtService.signIdToken(
                issuer,
                user,
                clientId,
                scopes,
                now.plus(ttl(config.getIdTokenTtlMinutes(), ID_TOKEN_TTL)),
                nonce,
                splitValues(config.getIdTokenClaims()),
                splitEntries(config.getCustomClaims()))
            : null;
        accessTokens.save(new OAuthAccessToken(
            tokens.sha256(accessToken),
            idToken == null ? null : tokens.sha256(idToken),
            config.getApplication(),
            user,
            clientId,
            scopes,
            now.plus(accessTokenTtl),
            refreshTokenId));
        return new TokenResponse(accessToken, "Bearer", accessTokenTtl.toSeconds(), refreshToken, idToken, scopes);
    }

    @Transactional(readOnly = true)
    // 根据 Bearer access token 返回 OIDC UserInfo 声明，按授权 scope 裁剪返回字段。
    public UserInfoResponse userInfo(String authorizationHeader) {
        String bearerToken = extractBearerToken(authorizationHeader);
        OAuthAccessToken token = accessTokens.findByTokenHash(tokens.sha256(bearerToken))
            .orElseThrow(() -> OAuthException.invalidToken("Access token is invalid"));
        if (!token.isActive(Instant.now())) {
            throw OAuthException.invalidToken("Access token is expired or revoked");
        }
        ApplicationSsoConfig config = ssoConfigs.findByClientId(token.getClientId())
            .filter(ApplicationSsoConfig::isEnabled)
            .orElseThrow(() -> OAuthException.invalidToken("OAuth client is disabled"));
        UserAccount user = token.getUser();
        if (!access.decideApplicationAccess(config.getApplication().getId(), user.getId()).allowed()) {
            throw OAuthException.invalidToken("User is no longer authorized for this application");
        }
        Set<String> scopes = splitScopes(token.getScopes());
        boolean profile = scopes.contains("profile");
        return new UserInfoResponse(
            user.getId().toString(),
            profile ? user.getUsername() : null,
            profile ? user.getDisplayName() : null,
            scopes.contains("email") ? user.getEmail() : null,
            scopes.contains("phone") ? user.getMobile() : null,
            access.decideApplicationPermissions(config.getApplication().getId(), user.getId()).permissions());
    }

    // 生成 OIDC Provider Discovery 元数据。
    public OidcDiscoveryResponse discovery(String issuer) {
        String normalized = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return new OidcDiscoveryResponse(
            normalized,
            normalized + "/oidc/authorize",
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
            List.of("client_secret_basic", "client_secret_post"),
            List.of("sub", "iss", "aud", "exp", "iat", "nonce", "preferred_username", "name", "email", "phone_number"));
    }

    // 浏览器授权流程使用：应用停用由后续的访问授权判定统一返回 403 application_disabled。
    private ApplicationSsoConfig getOauthClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw OAuthException.invalidRequest("client_id is required");
        }
        ApplicationSsoConfig config = ssoConfigs.findByClientId(clientId)
            .orElseThrow(() -> OAuthException.invalidClient("OAuth client not found: " + clientId));
        if (config.getProtocol() != ApplicationProtocol.OIDC && config.getProtocol() != ApplicationProtocol.OAUTH2) {
            throw new OAuthException("unauthorized_client", "Application is not configured for OAuth2/OIDC");
        }
        if (!config.isEnabled()) {
            throw new OAuthException("unauthorized_client", "OAuth client is disabled");
        }
        return config;
    }

    // token / introspect / revoke 端点使用：校验客户端凭据，并要求应用处于启用状态。
    private ApplicationSsoConfig authenticatedClient(String clientId, String clientSecret) {
        ApplicationSsoConfig config = getOauthClient(clientId);
        validateClientSecret(config, clientSecret);
        if (!config.getApplication().isEnabled()) {
            throw new OAuthException("unauthorized_client", "Application is disabled");
        }
        return config;
    }

    private boolean allowsGrantType(ApplicationSsoConfig config, String grantType) {
        Set<String> configured = splitValues(config.getGrantTypes());
        return (configured.isEmpty() ? DEFAULT_GRANT_TYPES : configured).contains(grantType);
    }

    private void requireGrantType(ApplicationSsoConfig config, String grantType) {
        if (!allowsGrantType(config, grantType)) {
            throw new OAuthException("unauthorized_client", "Client is not allowed to use grant type: " + grantType);
        }
    }

    private void requireUserAccess(ApplicationSsoConfig config, UserAccount user) {
        var decision = access.decideApplicationAccess(config.getApplication().getId(), user.getId());
        if (!decision.allowed()) {
            throw OAuthException.invalidGrant("User is not authorized to access this application: " + decision.reason());
        }
    }

    private Duration ttl(int minutes, Duration fallback) {
        return minutes > 0 ? Duration.ofMinutes(minutes) : fallback;
    }

    private void validateRedirectUri(ApplicationSsoConfig config, String redirectUri) {
        if (redirectUri == null || !splitValues(config.getRedirectUris()).contains(redirectUri)) {
            throw OAuthException.invalidRequest("redirect_uri is not registered for client");
        }
    }

    private Set<String> validateScopes(ApplicationSsoConfig config, String requestedScope) {
        Set<String> registeredScopes = splitValues(config.getScopes());
        Set<String> requestedScopes = splitScopes(requestedScope);
        if (requestedScopes.isEmpty()) {
            return registeredScopes;
        }
        if (!registeredScopes.containsAll(requestedScopes)) {
            throw new OAuthException("invalid_scope", "Requested scope exceeds registered scopes");
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
        if (clientSecret == null || clientSecret.isBlank()
            || config.getClientSecretHash() == null
            || !passwordEncoder.matches(clientSecret, config.getClientSecretHash())) {
            throw OAuthException.invalidClient("Invalid client credentials");
        }
    }

    private void validatePkceChallenge(boolean required, String codeChallenge, String codeChallengeMethod) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            if (required) {
                throw OAuthException.invalidRequest("code_challenge is required");
            }
            return;
        }
        String method = normalizeCodeChallengeMethod(codeChallengeMethod);
        if (!"S256".equals(method) && !"plain".equals(method)) {
            throw OAuthException.invalidRequest("Unsupported code_challenge_method: " + method);
        }
        if (!isValidPkceValue(codeChallenge) || ("S256".equals(method) && codeChallenge.length() != 43)) {
            throw OAuthException.invalidRequest("Invalid code_challenge");
        }
    }

    private void validatePkceVerifier(OAuthAuthorizationCode authorizationCode, String codeVerifier) {
        if (authorizationCode.getCodeChallenge() == null || authorizationCode.getCodeChallenge().isBlank()) {
            return;
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw OAuthException.invalidGrant("code_verifier is required");
        }
        if (!isValidPkceValue(codeVerifier)) {
            throw OAuthException.invalidGrant("Invalid code_verifier");
        }
        String expected = "S256".equals(authorizationCode.getCodeChallengeMethod())
            ? s256(codeVerifier)
            : codeVerifier;
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            authorizationCode.getCodeChallenge().getBytes(StandardCharsets.UTF_8))) {
            throw OAuthException.invalidGrant("Invalid code_verifier");
        }
    }

    private boolean isValidPkceValue(String value) {
        if (value.length() < 43 || value.length() > 128) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                && !(character >= 'a' && character <= 'z')
                && !(character >= '0' && character <= '9')
                && character != '-' && character != '.' && character != '_' && character != '~') {
                return false;
            }
        }
        return true;
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
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw OAuthException.invalidToken("Bearer access token is required");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw OAuthException.invalidToken("Bearer access token is required");
        }
        return token;
    }

    private String buildRedirect(String redirectUri, String code, String state) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        String url = redirectUri + separator + "code=" + urlEncode(code);
        if (state != null && !state.isBlank()) {
            url += "&state=" + urlEncode(state);
        }
        return url;
    }

    private String consentRedirect(String clientId, String redirectUri, String scope, String state, String nonce, String codeChallenge, String codeChallengeMethod) {
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
        if (nonce != null && !nonce.isBlank()) {
            target.append("&nonce=").append(urlEncode(nonce));
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
            throw OAuthException.invalidRequest("token is required");
        }
        return token;
    }

    private TokenIntrospectionResponse toIntrospectionResponse(OAuthAccessToken accessToken, Instant now) {
        if (!accessToken.isActive(now) || !isGrantHolderActive(accessToken.getApplication(), accessToken.getUser())) {
            return TokenIntrospectionResponse.inactive();
        }
        return new TokenIntrospectionResponse(
            true,
            accessToken.getClientId(),
            accessToken.getUser().getUsername(),
            accessToken.getUser().getId().toString(),
            "Bearer",
            accessToken.getScopes(),
            accessToken.getExpiresAt().getEpochSecond(),
            accessToken.getCreatedAt() == null ? null : accessToken.getCreatedAt().getEpochSecond(),
            access.decideApplicationPermissions(accessToken.getApplication().getId(), accessToken.getUser().getId()).permissions());
    }

    private TokenIntrospectionResponse toIntrospectionResponse(OAuthRefreshToken refreshToken, Instant now) {
        if (!refreshToken.isActive(now) || !isGrantHolderActive(refreshToken.getApplication(), refreshToken.getUser())) {
            return TokenIntrospectionResponse.inactive();
        }
        return new TokenIntrospectionResponse(
            true,
            refreshToken.getClientId(),
            refreshToken.getUser().getUsername(),
            refreshToken.getUser().getId().toString(),
            "refresh_token",
            refreshToken.getScopes(),
            refreshToken.getExpiresAt().getEpochSecond(),
            refreshToken.getCreatedAt() == null ? null : refreshToken.getCreatedAt().getEpochSecond());
    }

    private boolean isGrantHolderActive(com.antiam.domain.Application application, UserAccount user) {
        return application.isEnabled() && LoginSessionService.signInBlockReason(user) == null;
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
