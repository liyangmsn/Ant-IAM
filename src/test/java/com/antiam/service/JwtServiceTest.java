package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.common.TokenSupport;
import com.antiam.domain.JwtSigningKey;
import com.antiam.mapper.JwtMapper;
import com.antiam.repository.JwtSigningKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtSigningKeyRepository signingKeys = mock(JwtSigningKeyRepository.class);
    private final Map<String, JwtSigningKey> stored = new HashMap<>();
    private final JwtService service = new JwtService(
        signingKeys,
        new TokenSupport(),
        mock(JwtMapper.class),
        mock(AuditService.class),
        new ObjectMapper());

    @BeforeEach
    void setUp() {
        stored.clear();
        when(signingKeys.save(any(JwtSigningKey.class))).thenAnswer(invocation -> {
            JwtSigningKey key = invocation.getArgument(0);
            stored.put(key.getKeyId(), key);
            return key;
        });
        when(signingKeys.findFirstByRetiredAtIsNullOrderByActivatedAtDesc())
            .thenAnswer(invocation -> stored.values().stream().findFirst());
        when(signingKeys.findByKeyId(anyString()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0, String.class))));
    }

    @Test
    void signsAndVerifiesTokenWithGeneratedKey() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String token = service.signToken(
            "https://iam.example.com",
            "user-1",
            "checkout-app",
            issuedAt,
            issuedAt.plusSeconds(600),
            Map.of("preferred_username", "alice"));

        JwtService.TokenVerification verification = service.verify(token);

        assertThat(verification.valid()).isTrue();
        assertThat(verification.failureCode()).isNull();
        assertThat(verification.issuer()).isEqualTo("https://iam.example.com");
        assertThat(verification.audience()).isEqualTo("checkout-app");
        assertThat(verification.subject()).isEqualTo("user-1");
        assertThat(verification.issuedAt()).isEqualTo(issuedAt);
        assertThat(verification.expiresAt()).isEqualTo(issuedAt.plusSeconds(600));
        assertThat(verification.keyId()).isNotBlank();
        assertThat(verification.claims()).containsEntry("preferred_username", "alice");
    }

    @Test
    void reusesActiveKeyAcrossSignatures() {
        Instant now = Instant.now();

        String first = service.signToken("https://iam.example.com", "user-1", "checkout-app", now, now.plusSeconds(60), Map.of());
        String second = service.signToken("https://iam.example.com", "user-2", "checkout-app", now, now.plusSeconds(60), Map.of());

        assertThat(service.verify(first).keyId()).isEqualTo(service.verify(second).keyId());
        assertThat(stored).hasSize(1);
    }

    @Test
    void rejectsTamperedPayload() {
        Instant now = Instant.now();
        String token = service.signToken("https://iam.example.com", "user-1", "checkout-app", now, now.plusSeconds(60), Map.of());
        String[] parts = token.split("\\.");

        String forged = parts[0] + "." + segment("{\"iss\":\"https://iam.example.com\",\"sub\":\"attacker\",\"aud\":\"checkout-app\",\"exp\":9999999999}") + "." + parts[2];

        assertThat(service.verify(forged).failureCode()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    void rejectsUnsupportedAlgorithm() {
        String forged = segment("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + segment("{\"sub\":\"user-1\"}") + ".x";

        JwtService.TokenVerification verification = service.verify(forged);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("UNSUPPORTED_ALGORITHM");
    }

    @Test
    void rejectsUnknownKeyId() {
        String forged = segment("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"missing\"}") + "." + segment("{\"sub\":\"user-1\"}") + ".x";

        JwtService.TokenVerification verification = service.verify(forged);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("UNKNOWN_KEY");
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        String token = service.signToken(
            "https://iam.example.com",
            "user-1",
            "checkout-app",
            now.minusSeconds(1_200),
            now.minusSeconds(600),
            Map.of());

        JwtService.TokenVerification verification = service.verify(token);

        assertThat(verification.valid()).isFalse();
        assertThat(verification.failureCode()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(service.verify("not-a-token").failureCode()).isEqualTo("MALFORMED_TOKEN");
        assertThat(service.verify("").failureCode()).isEqualTo("MALFORMED_TOKEN");
        assertThat(service.verify(null).failureCode()).isEqualTo("MALFORMED_TOKEN");
    }

    private static String segment(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
