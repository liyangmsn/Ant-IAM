package com.antiam.domain;

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
@Table(name = "identity_sync_jobs")
public class IdentitySyncJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_source_id", nullable = false)
    private IdentitySource identitySource;

    private String name;

    @Enumerated(EnumType.STRING)
    private IdentitySyncMode mode;

    private String cronExpression;
    private boolean enabled;

    public IdentitySyncJob(IdentitySource identitySource, String name, IdentitySyncMode mode, String cronExpression) {
        this.identitySource = identitySource;
        this.name = name;
        this.mode = mode;
        this.cronExpression = cronExpression;
        this.enabled = true;
    }

    public void update(String name, IdentitySyncMode mode, String cronExpression) {
        this.name = name;
        this.mode = mode;
        this.cronExpression = cronExpression;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
