package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "oauth_refresh_tokens")
public class OAuthRefreshToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String clientId;

    @Column(columnDefinition = "text")
    private String scopes;

    private Instant expiresAt;
    private Instant revokedAt;
    private Instant lastUsedAt;

    public OAuthRefreshToken(
        String tokenHash,
        Application application,
        UserAccount user,
        String clientId,
        String scopes,
        Instant expiresAt
    ) {
        this.tokenHash = tokenHash;
        this.application = application;
        this.user = user;
        this.clientId = clientId;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
