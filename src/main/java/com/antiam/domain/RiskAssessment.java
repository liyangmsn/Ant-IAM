package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    private String geoLocation;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(columnDefinition = "text")
    private String matchedRules;

    @Column(columnDefinition = "text")
    private String decision;

    public RiskAssessment(
        UserAccount user,
        String ipAddress,
        String userAgent,
        String deviceFingerprint,
        String geoLocation,
        RiskLevel riskLevel,
        String matchedRules,
        String decision
    ) {
        this.user = user;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.deviceFingerprint = deviceFingerprint;
        this.geoLocation = geoLocation;
        this.riskLevel = riskLevel;
        this.matchedRules = matchedRules;
        this.decision = decision;
    }
}
