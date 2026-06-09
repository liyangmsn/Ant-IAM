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
@Table(name = "mfa_factors")
public class MfaFactor extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    private MfaFactorType type;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String secret;

    private boolean verified;
    private boolean enabled;

    public MfaFactor(UserAccount user, MfaFactorType type, String name, String secret) {
        this.user = user;
        this.type = type;
        this.name = name;
        this.secret = secret;
        this.enabled = true;
        this.verified = false;
    }

    public void verify() {
        this.verified = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void replaceSecret(String secret) {
        this.secret = secret;
    }
}
