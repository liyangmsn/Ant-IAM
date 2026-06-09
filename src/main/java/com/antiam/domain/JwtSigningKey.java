package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "jwt_signing_keys")
public class JwtSigningKey extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String keyId;

    @Column(columnDefinition = "text", nullable = false)
    private String publicKeyPem;

    @Column(columnDefinition = "text", nullable = false)
    private String privateKeyPem;

    private Instant activatedAt;
    private Instant retiredAt;

    public JwtSigningKey(String keyId, String publicKeyPem, String privateKeyPem) {
        this.keyId = keyId;
        this.publicKeyPem = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
        this.activatedAt = Instant.now();
    }

    public boolean isActive() {
        return retiredAt == null;
    }

    public void retire() {
        retiredAt = Instant.now();
    }
}
