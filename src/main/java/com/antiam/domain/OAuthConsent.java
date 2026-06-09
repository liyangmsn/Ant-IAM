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
@Table(name = "oauth_consents")
public class OAuthConsent extends BaseEntity {

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

    private Instant grantedAt;
    private Instant revokedAt;

    public OAuthConsent(Application application, UserAccount user, String clientId, String scopes) {
        this.application = application;
        this.user = user;
        this.clientId = clientId;
        this.scopes = scopes;
        this.grantedAt = Instant.now();
    }

    public void replaceScopes(String scopes) {
        this.scopes = scopes;
        this.grantedAt = Instant.now();
        this.revokedAt = null;
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
