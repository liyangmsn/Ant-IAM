package com.antiam.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JwkDtos {
    private JwkDtos() {
    }

    public record JsonWebKey(String kty, String use, String kid, String alg, String n, String e) {
    }

    public record JwksResponse(List<JsonWebKey> keys) {
    }

    public record SigningKeyResponse(UUID id, String keyId, boolean active, Instant activatedAt, Instant retiredAt) {
    }
}
