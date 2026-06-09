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
@Table(name = "mfa_challenges")
public class MfaChallenge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factor_id", nullable = false)
    private MfaFactor factor;

    @Column(nullable = false, unique = true)
    private String challengeId;

    @Column(nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    private MfaChallengeStatus status;

    private Instant expiresAt;
    private Instant verifiedAt;
    private int attempts;

    public MfaChallenge(UserAccount user, MfaFactor factor, String challengeId, String codeHash, Instant expiresAt) {
        this.user = user;
        this.factor = factor;
        this.challengeId = challengeId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.status = MfaChallengeStatus.PENDING;
    }

    public boolean isUsable(Instant now) {
        return status == MfaChallengeStatus.PENDING && expiresAt.isAfter(now);
    }

    public void verify() {
        this.status = MfaChallengeStatus.VERIFIED;
        this.verifiedAt = Instant.now();
    }

    public void fail() {
        this.status = MfaChallengeStatus.FAILED;
        this.attempts++;
    }

    public void expire() {
        this.status = MfaChallengeStatus.EXPIRED;
    }
}
