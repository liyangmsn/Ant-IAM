package com.antiam.service;

import static com.antiam.dto.JwkDtos.JsonWebKey;
import static com.antiam.dto.JwkDtos.JwksResponse;
import static com.antiam.dto.JwkDtos.SigningKeyResponse;

import com.antiam.common.NotFoundException;
import com.antiam.common.SelfSignedCertificates;
import com.antiam.common.TokenSupport;
import com.antiam.domain.JwtSigningKey;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.JwtMapper;
import com.antiam.repository.JwtSigningKeyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtSigningKeyRepository signingKeys;
    private final TokenSupport tokens;
    private final JwtMapper jwtMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    // 自签名证书按 keyId 缓存；证书由密钥确定性生成，缓存仅为避免重复计算。
    private final Map<String, X509Certificate> certificates = new java.util.concurrent.ConcurrentHashMap<>();

    // 协议保留声明，应用自定义声明不能覆盖，避免篡改签发方、受众和有效期。
    private static final Set<String> RESERVED_CLAIMS = Set.of(
        "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "nonce", "auth_time", "azp", "at_hash", "c_hash", "typ");

    @Transactional
    // 使用当前活跃 RSA 密钥签发 OIDC ID Token。
    public String signIdToken(
        String issuer,
        UserAccount user,
        String clientId,
        String scopes,
        Instant expiresAt,
        String nonce,
        Set<String> idTokenClaims,
        Map<String, String> customClaims
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", user.getId().toString());
        payload.put("aud", clientId);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        if (nonce != null) {
            payload.put("nonce", nonce);
        }
        claimNames(idTokenClaims).forEach(claim -> addClaim(payload, claim, user, scopes));
        putCustomClaims(payload, customClaims);
        return sign(payload);
    }

    @Transactional
    // 使用当前活跃 RSA 密钥签发通用 JWT，供 JWT 单点登录等非 OIDC 场景使用。
    public String signToken(
        String issuer,
        String subject,
        String audience,
        Instant issuedAt,
        Instant expiresAt,
        Map<String, String> claims
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", subject);
        payload.put("aud", audience);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        putCustomClaims(payload, claims);
        return sign(payload);
    }

    private void putCustomClaims(Map<String, Object> payload, Map<String, String> claims) {
        if (claims == null) {
            return;
        }
        claims.forEach((name, value) -> {
            if (name != null && !name.isBlank() && !RESERVED_CLAIMS.contains(name.trim())) {
                payload.put(name.trim(), value);
            }
        });
    }

    private String sign(Map<String, Object> payload) {
        JwtSigningKey key = activeKey();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", key.getKeyId());
        String signingInput = base64Url(toJson(header)) + "." + base64Url(toJson(payload));
        return signingInput + "." + base64Url(sign(signingInput, key.getPrivateKeyPem()));
    }

    private byte[] toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize JWT", ex);
        }
    }

    @Transactional(readOnly = true)
    // 仅校验签名与时间，不比对签发方和受众。
    public TokenVerification verify(String token) {
        return verify(token, null, null);
    }

    @Transactional(readOnly = true)
    // 校验 JWT：算法固定 RS256，按头部 kid 匹配未退役的签名密钥，要求 exp，并在给定时比对 iss 与 aud。
    public TokenVerification verify(String token, String expectedIssuer, String expectedAudience) {
        if (token == null || token.isBlank()) {
            return TokenVerification.failure("MALFORMED_TOKEN", "Token is empty");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return TokenVerification.failure("MALFORMED_TOKEN", "Token must contain three non-empty segments");
        }
        JsonNode header = decodeJson(parts[0]);
        if (header == null) {
            return TokenVerification.failure("MALFORMED_TOKEN", "Token header is not valid JSON");
        }
        if (!"RS256".equals(header.path("alg").asText(""))) {
            return TokenVerification.failure("UNSUPPORTED_ALGORITHM", "Only RS256 tokens are supported");
        }
        if (header.hasNonNull("typ") && !"JWT".equalsIgnoreCase(header.get("typ").asText())) {
            return TokenVerification.failure("UNSUPPORTED_TYPE", "Token typ must be JWT");
        }
        String keyId = header.path("kid").asText("");
        if (keyId.isBlank()) {
            return TokenVerification.failure("UNKNOWN_KEY", "Token header does not carry kid");
        }
        JwtSigningKey key = signingKeys.findByKeyId(keyId).orElse(null);
        if (key == null) {
            return TokenVerification.failure("UNKNOWN_KEY", "No signing key matches kid " + keyId);
        }
        if (!key.isActive()) {
            return TokenVerification.failure("KEY_RETIRED", "Signing key " + keyId + " has been retired");
        }
        if (!verifySignature(parts[0] + "." + parts[1], parts[2], key.getPublicKeyPem())) {
            return TokenVerification.failure("INVALID_SIGNATURE", "Token signature verification failed");
        }
        JsonNode payload = decodeJson(parts[1]);
        if (payload == null) {
            return TokenVerification.failure("MALFORMED_TOKEN", "Token payload is not valid JSON");
        }
        long now = Instant.now().getEpochSecond();
        if (!payload.hasNonNull("exp") || !payload.get("exp").canConvertToLong()) {
            return TokenVerification.failure("MISSING_EXPIRATION", "Token does not carry a numeric exp claim");
        }
        if (payload.get("exp").asLong() <= now) {
            return TokenVerification.failure("TOKEN_EXPIRED", "Token has expired");
        }
        if (payload.hasNonNull("nbf") && payload.get("nbf").asLong() > now) {
            return TokenVerification.failure("TOKEN_NOT_YET_VALID", "Token is not valid yet");
        }
        if (expectedIssuer != null && !expectedIssuer.isBlank() && !expectedIssuer.equals(payload.path("iss").asText(null))) {
            return TokenVerification.failure("INVALID_ISSUER", "Token issuer does not match " + expectedIssuer);
        }
        if (expectedAudience != null && !expectedAudience.isBlank() && !audiences(payload).contains(expectedAudience)) {
            return TokenVerification.failure("INVALID_AUDIENCE", "Token audience does not include " + expectedAudience);
        }
        return new TokenVerification(
            true,
            keyId,
            payload.path("iss").asText(null),
            audienceOf(payload),
            payload.path("sub").asText(null),
            instantAt(payload, "iat"),
            instantAt(payload, "exp"),
            objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {
            }),
            null,
            null);
    }

    @Transactional
    // 返回当前活跃签名密钥及其自签名证书，供 SAML 断言签名与元数据发布使用。
    public SigningMaterial activeSigningMaterial() {
        JwtSigningKey key = activeKey();
        X509Certificate certificate = certificates.computeIfAbsent(key.getKeyId(), keyId -> {
            try {
                Instant notBefore = key.getActivatedAt() == null ? Instant.EPOCH : key.getActivatedAt();
                return SelfSignedCertificates.create(
                    publicKey(key.getPublicKeyPem()),
                    privateKey(key.getPrivateKeyPem()),
                    "Ant IAM Signing " + keyId,
                    keyId,
                    notBefore,
                    notBefore.atZone(java.time.ZoneOffset.UTC).plusYears(10).toInstant());
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to read signing key", ex);
            }
        });
        try {
            return new SigningMaterial(key.getKeyId(), privateKey(key.getPrivateKeyPem()), certificate);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to read signing key", ex);
        }
    }

    @Transactional
    // 输出 JWKS 公钥集；没有活跃密钥时会自动生成首把签名密钥。
    public JwksResponse jwks() {
        List<JwtSigningKey> keys = signingKeys.findByRetiredAtIsNullOrderByActivatedAtDesc();
        if (keys.isEmpty()) {
            keys = List.of(generateKey());
        }
        return new JwksResponse(keys.stream().map(this::toJwk).toList());
    }

    @Transactional(readOnly = true)
    // 查询 JWT 签名密钥元数据，支持活跃状态和 keyId 关键字过滤。
    public List<SigningKeyResponse> listKeys(Boolean active, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return signingKeys.findAllByOrderByActivatedAtDesc().stream()
            .filter(key -> active == null || key.isActive() == active)
            .filter(key -> normalizedKeyword == null || key.getKeyId().toLowerCase().contains(normalizedKeyword))
            .map(jwtMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询单个 JWT 签名密钥元数据。
    public SigningKeyResponse getKey(java.util.UUID keyId) {
        return signingKeys.findById(keyId)
            .map(jwtMapper::toResponse)
            .orElseThrow(() -> new NotFoundException("JWT signing key not found: " + keyId));
    }

    @Transactional
    // 轮换 JWT 签名密钥，创建新的活跃密钥用于后续签发。
    public SigningKeyResponse rotateKey(String actor) {
        JwtSigningKey key = generateKey();
        auditService.record(actor, "jwt_signing_key.rotate", "jwt_signing_key", key.getId().toString(), key.getKeyId());
        return jwtMapper.toResponse(key);
    }

    @Transactional
    // 退役 JWT 签名密钥，并保护最后一把活跃密钥不被误退役。
    public SigningKeyResponse retireKey(java.util.UUID keyId, String actor) {
        JwtSigningKey key = signingKeys.findById(keyId)
            .orElseThrow(() -> new NotFoundException("JWT signing key not found: " + keyId));
        if (key.isActive() && signingKeys.findByRetiredAtIsNullOrderByActivatedAtDesc().size() <= 1) {
            throw new IllegalArgumentException("Cannot retire the last active JWT signing key; rotate a new key first");
        }
        key.retire();
        auditService.record(actor, "jwt_signing_key.retire", "jwt_signing_key", key.getId().toString(), key.getKeyId());
        return jwtMapper.toResponse(key);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private JsonWebKey toJwk(JwtSigningKey key) {
        RSAPublicKey publicKey = publicKey(key.getPublicKeyPem());
        return new JsonWebKey(
            "RSA",
            "sig",
            key.getKeyId(),
            "RS256",
            unsignedInteger(publicKey.getModulus().toByteArray()),
            unsignedInteger(publicKey.getPublicExponent().toByteArray()));
    }

    private JwtSigningKey activeKey() {
        return signingKeys.findFirstByRetiredAtIsNullOrderByActivatedAtDesc()
            .orElseGet(this::generateKey);
    }

    private JwtSigningKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return signingKeys.save(new JwtSigningKey(
                tokens.generateToken(12),
                pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                pem("PRIVATE KEY", pair.getPrivate().getEncoded())));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to generate JWT signing key", ex);
        }
    }

    private byte[] sign(String signingInput, String privateKeyPem) {
        try {
            PrivateKey privateKey = privateKey(privateKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign JWT", ex);
        }
    }

    private PrivateKey privateKey(String pem) throws GeneralSecurityException {
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }

    private RSAPublicKey publicKey(String pem) {
        try {
            String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to read JWT public key", ex);
        }
    }

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded)
            + "\n-----END " + type + "-----";
    }

    private Set<String> claimNames(Set<String> configuredClaims) {
        if (configuredClaims == null || configuredClaims.isEmpty()) {
            return new LinkedHashSet<>(List.of("preferred_username", "name", "email", "scope"));
        }
        return configuredClaims;
    }

    private void addClaim(Map<String, Object> payload, String claim, UserAccount user, String scopes) {
        switch (claim) {
            case "preferred_username" -> payload.put(claim, user.getUsername());
            case "name" -> payload.put(claim, user.getDisplayName());
            case "email" -> payload.put(claim, user.getEmail());
            case "phone_number" -> payload.put(claim, user.getMobile());
            case "tenant_id" -> payload.put(claim, user.getTenant() == null ? null : user.getTenant().getId().toString());
            case "organization_id" -> payload.put(claim, user.getOrganization() == null ? null : user.getOrganization().getId().toString());
            case "scope" -> payload.put(claim, scopes);
            default -> {
            }
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private JsonNode decodeJson(String segment) {
        try {
            return objectMapper.readTree(Base64.getUrlDecoder().decode(segment));
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant instantAt(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? Instant.ofEpochSecond(payload.get(field).asLong()) : null;
    }

    private List<String> audiences(JsonNode payload) {
        JsonNode audience = payload.get("aud");
        List<String> values = new ArrayList<>();
        if (audience == null || audience.isNull()) {
            return values;
        }
        if (audience.isArray()) {
            audience.forEach(node -> values.add(node.asText()));
        } else {
            values.add(audience.asText());
        }
        return values;
    }

    private String audienceOf(JsonNode payload) {
        JsonNode audience = payload.get("aud");
        if (audience == null || audience.isNull()) {
            return null;
        }
        if (audience.isArray()) {
            List<String> values = new ArrayList<>();
            audience.forEach(node -> values.add(node.asText()));
            return String.join(",", values);
        }
        return audience.asText();
    }

    private boolean verifySignature(String signingInput, String signature, String publicKeyPem) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey(publicKeyPem));
            verifier.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getUrlDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private String unsignedInteger(byte[] value) {
        int offset = value.length > 1 && value[0] == 0 ? 1 : 0;
        return base64Url(java.util.Arrays.copyOfRange(value, offset, value.length));
    }

    // 签名私钥与对应自签名证书。
    public record SigningMaterial(String keyId, PrivateKey privateKey, X509Certificate certificate) {
    }

    // JWT 校验结果，失败时由 failureCode / failureMessage 说明原因。
    public record TokenVerification(
        boolean valid,
        String keyId,
        String issuer,
        String audience,
        String subject,
        Instant issuedAt,
        Instant expiresAt,
        Map<String, Object> claims,
        String failureCode,
        String failureMessage
    ) {

        static TokenVerification failure(String code, String message) {
            return new TokenVerification(false, null, null, null, null, null, null, Map.of(), code, message);
        }
    }
}
