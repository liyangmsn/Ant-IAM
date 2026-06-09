package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_credentials")
public class UserCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    private CredentialType type;

    @Column(nullable = false)
    private String secretHash;

    private boolean temporary;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private int failedAttempts;
    private Instant lastFailedAt;
    private Instant lockedAt;

    public UserCredential(UserAccount user, CredentialType type, String secretHash, boolean temporary) {
        this(user, type, secretHash, temporary, null);
    }

    public UserCredential(UserAccount user, CredentialType type, String secretHash, boolean temporary, Instant expiresAt) {
        this.user = user;
        this.type = type;
        this.secretHash = secretHash;
        this.temporary = temporary;
        this.expiresAt = expiresAt;
    }

    public void rotate(String secretHash, boolean temporary, Instant expiresAt) {
        this.secretHash = secretHash;
        this.temporary = temporary;
        this.expiresAt = expiresAt;
        resetFailures();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
        resetFailures();
    }

    public void markFailed() {
        this.failedAttempts++;
        this.lastFailedAt = Instant.now();
    }

    public void markLocked() {
        this.lockedAt = Instant.now();
    }

    public void resetFailures() {
        this.failedAttempts = 0;
        this.lastFailedAt = null;
        this.lockedAt = null;
    }
}
