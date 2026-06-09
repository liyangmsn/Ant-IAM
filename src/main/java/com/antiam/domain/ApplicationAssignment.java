package com.antiam.domain;

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
@Table(name = "application_assignments")
public class ApplicationAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private UserGroup group;

    private boolean enabled;
    private Instant expiresAt;

    public ApplicationAssignment(Application application, UserAccount user, UserGroup group) {
        this(application, user, group, null);
    }

    public ApplicationAssignment(Application application, UserAccount user, UserGroup group, Instant expiresAt) {
        this.application = application;
        this.user = user;
        this.group = group;
        this.expiresAt = expiresAt;
        this.enabled = true;
    }

    public void enable() {
        this.enabled = true;
    }

    public void updateExpiry(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return enabled && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void disable() {
        this.enabled = false;
    }
}
