package com.antiam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "authentication_policies")
public class AuthenticationPolicy extends BaseEntity {

    private String code;
    private String name;
    private int priority;
    private boolean mfaRequired;
    private boolean mfaEnrollmentRequired;
    private int passwordMinLength;
    @Enumerated(EnumType.STRING)
    private RiskLevel stepUpRiskLevel;
    @Enumerated(EnumType.STRING)
    private RiskLevel denyRiskLevel;
    private int passwordMaxFailureAttempts;
    private int passwordExpiresInDays;
    private int passwordHistoryCount;
    private boolean enabled;

    public AuthenticationPolicy(
        String code,
        String name,
        int priority,
        boolean mfaRequired,
        boolean mfaEnrollmentRequired,
        int passwordMinLength,
        RiskLevel stepUpRiskLevel,
        RiskLevel denyRiskLevel
    ) {
        this.code = code;
        this.name = name;
        this.priority = priority;
        this.mfaRequired = mfaRequired;
        this.mfaEnrollmentRequired = mfaEnrollmentRequired;
        this.passwordMinLength = passwordMinLength;
        this.stepUpRiskLevel = stepUpRiskLevel == null ? RiskLevel.MEDIUM : stepUpRiskLevel;
        this.denyRiskLevel = denyRiskLevel == null ? RiskLevel.HIGH : denyRiskLevel;
        this.passwordMaxFailureAttempts = 5;
        this.passwordExpiresInDays = 0;
        this.passwordHistoryCount = 0;
        this.enabled = true;
    }

    public void update(
        String name,
        int priority,
        boolean mfaRequired,
        boolean mfaEnrollmentRequired,
        int passwordMinLength,
        RiskLevel stepUpRiskLevel,
        RiskLevel denyRiskLevel
    ) {
        this.name = name;
        this.priority = priority;
        this.mfaRequired = mfaRequired;
        this.mfaEnrollmentRequired = mfaEnrollmentRequired;
        this.passwordMinLength = passwordMinLength;
        this.stepUpRiskLevel = stepUpRiskLevel == null ? RiskLevel.MEDIUM : stepUpRiskLevel;
        this.denyRiskLevel = denyRiskLevel == null ? RiskLevel.HIGH : denyRiskLevel;
    }

    public void updatePasswordFailurePolicy(int passwordMaxFailureAttempts) {
        this.passwordMaxFailureAttempts = passwordMaxFailureAttempts;
    }

    public void updatePasswordExpiryPolicy(int passwordExpiresInDays) {
        this.passwordExpiresInDays = passwordExpiresInDays;
    }

    public void updatePasswordHistoryPolicy(int passwordHistoryCount) {
        this.passwordHistoryCount = passwordHistoryCount;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
