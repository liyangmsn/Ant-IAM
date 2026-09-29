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

    public static final String PURPOSE_GENERAL = "GENERAL";
    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final int MAX_ATTEMPTS = 5;

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

    @Column(nullable = false)
    private String purpose = PURPOSE_GENERAL;

    public MfaChallenge(UserAccount user, MfaFactor factor, String challengeId, String codeHash, Instant expiresAt) {
        this.user = user;
        this.factor = factor;
        this.challengeId = challengeId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.status = MfaChallengeStatus.PENDING;
    }

    public MfaChallenge(UserAccount user, MfaFactor factor, String challengeId, String codeHash, Instant expiresAt, String purpose) {
        this(user, factor, challengeId, codeHash, expiresAt);
        this.purpose = purpose;
    }

    public boolean isUsable(Instant now) {
        return status == MfaChallengeStatus.PENDING && expiresAt.isAfter(now);
    }

    public void verify() {
        this.status = MfaChallengeStatus.VERIFIED;
        this.verifiedAt = Instant.now();
    }

    /**
     * 记录一次校验失败，达到最大尝试次数后挑战失效。
     */
    public void fail() {
        this.attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            this.status = MfaChallengeStatus.FAILED;
        }
    }

    /**
     * 切换 MFA 因子时沿用原挑战的失败次数，避免通过切换绕过尝试上限。
     */
    public void inheritAttempts(int attempts) {
        this.attempts = attempts;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = MfaChallengeStatus.FAILED;
        }
    }

    public void expire() {
        this.status = MfaChallengeStatus.EXPIRED;
    }
}
