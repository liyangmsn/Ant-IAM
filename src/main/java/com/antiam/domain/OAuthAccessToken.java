package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "oauth_access_tokens")
public class OAuthAccessToken extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String tokenHash;

    // 仅 OIDC 且包含 openid scope 时签发 ID Token，其余情况为空。
    @Column(unique = true)
    private String idTokenHash;

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

    // 签发该 access token 时配套的 refresh token，刷新或撤销 refresh token 时据此级联失效。
    private UUID refreshTokenId;

    public OAuthAccessToken(
        String tokenHash,
        String idTokenHash,
        Application application,
        UserAccount user,
        String clientId,
        String scopes,
        Instant expiresAt,
        UUID refreshTokenId
    ) {
        this.tokenHash = tokenHash;
        this.idTokenHash = idTokenHash;
        this.application = application;
        this.user = user;
        this.clientId = clientId;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
        this.refreshTokenId = refreshTokenId;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        if (this.revokedAt != null) {
            return;
        }
        this.revokedAt = Instant.now();
    }
}
