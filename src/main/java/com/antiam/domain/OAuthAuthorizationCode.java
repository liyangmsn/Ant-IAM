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
@Table(name = "oauth_authorization_codes")
public class OAuthAuthorizationCode extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String redirectUri;

    @Column(columnDefinition = "text")
    private String scopes;

    private String state;
    private String nonce;
    private String codeChallenge;
    private String codeChallengeMethod;
    private Instant expiresAt;
    private Instant consumedAt;

    public OAuthAuthorizationCode(
        String codeHash,
        Application application,
        UserAccount user,
        String clientId,
        String redirectUri,
        String scopes,
        String state,
        String nonce,
        String codeChallenge,
        String codeChallengeMethod,
        Instant expiresAt
    ) {
        this.codeHash = codeHash;
        this.application = application;
        this.user = user;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
        this.state = state;
        this.nonce = nonce;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
