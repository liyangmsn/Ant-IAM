package com.antiam.domain;

import jakarta.persistence.Column;
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
@Table(name = "risk_rules")
public class RiskRule extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private RiskRuleType type;

    @Column(columnDefinition = "text")
    private String conditionValue;

    private int threshold;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    private boolean enabled;

    public RiskRule(String code, String name, RiskRuleType type, String conditionValue, int threshold, RiskLevel riskLevel) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.conditionValue = conditionValue;
        this.threshold = threshold;
        this.riskLevel = riskLevel;
        this.enabled = true;
    }

    public void update(String name, RiskRuleType type, String conditionValue, int threshold, RiskLevel riskLevel) {
        this.name = name;
        this.type = type;
        this.conditionValue = conditionValue;
        this.threshold = threshold;
        this.riskLevel = riskLevel;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
