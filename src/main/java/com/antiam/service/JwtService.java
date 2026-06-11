package com.antiam.service;

import static com.antiam.dto.JwkDtos.JsonWebKey;
import static com.antiam.dto.JwkDtos.JwksResponse;
import static com.antiam.dto.JwkDtos.SigningKeyResponse;

import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.JwtSigningKey;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.JwtMapper;
import com.antiam.repository.JwtSigningKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Transactional
    // 使用当前活跃 RSA 密钥签发 OIDC ID Token。
    public String signIdToken(
        String issuer,
        UserAccount user,
        String clientId,
        String scopes,
        Instant expiresAt,
        Set<String> idTokenClaims,
        Map<String, String> customClaims
    ) {
        JwtSigningKey key = activeKey();
        String header = jsonObject(
            json("alg", "RS256"),
            json("typ", "JWT"),
            json("kid", key.getKeyId()));
        long now = Instant.now().getEpochSecond();
        List<String> fields = new ArrayList<>(List.of(
            json("iss", issuer),
            json("sub", user.getId().toString()),
            json("aud", clientId),
            json("iat", now),
            json("exp", expiresAt.getEpochSecond())));
        claimNames(idTokenClaims).forEach(claim -> addClaim(fields, claim, user, scopes));
        if (customClaims != null) {
            customClaims.forEach((name, value) -> {
                if (name != null && !name.isBlank()) {
                    fields.add(json(name, value));
                }
            });
        }
        String payload = jsonObject(fields.toArray(String[]::new));
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + base64Url(sign(signingInput, key.getPrivateKeyPem()));
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

    private String jsonObject(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private Set<String> claimNames(Set<String> configuredClaims) {
        if (configuredClaims == null || configuredClaims.isEmpty()) {
            return new LinkedHashSet<>(List.of("preferred_username", "name", "email", "scope"));
        }
        return configuredClaims;
    }

    private void addClaim(List<String> fields, String claim, UserAccount user, String scopes) {
        switch (claim) {
            case "preferred_username" -> fields.add(json(claim, user.getUsername()));
            case "name" -> fields.add(json(claim, user.getDisplayName()));
            case "email" -> fields.add(json(claim, user.getEmail()));
            case "phone_number" -> fields.add(json(claim, user.getMobile()));
            case "tenant_id" -> fields.add(json(claim, user.getTenant() == null ? null : user.getTenant().getId().toString()));
            case "organization_id" -> fields.add(json(claim, user.getOrganization() == null ? null : user.getOrganization().getId().toString()));
            case "scope" -> fields.add(json(claim, scopes));
            default -> {
            }
        }
    }

    private String json(String name, String value) {
        if (value == null) {
            return quote(name) + ":null";
        }
        return quote(name) + ":" + quote(value);
    }

    private String json(String name, long value) {
        return quote(name) + ":" + value;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String unsignedInteger(byte[] value) {
        int offset = value.length > 1 && value[0] == 0 ? 1 : 0;
        return base64Url(java.util.Arrays.copyOfRange(value, offset, value.length));
    }
}
