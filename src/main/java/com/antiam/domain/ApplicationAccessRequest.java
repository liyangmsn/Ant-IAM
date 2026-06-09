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
@Table(name = "application_access_requests")
public class ApplicationAccessRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    private ApplicationAccessRequestStatus status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(columnDefinition = "text")
    private String decisionReason;

    private String requestedBy;
    private String decidedBy;
    private Instant decidedAt;

    public ApplicationAccessRequest(Application application, UserAccount user, String reason, String requestedBy) {
        this.application = application;
        this.user = user;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.status = ApplicationAccessRequestStatus.PENDING;
    }

    public void approve(String actor, String decisionReason) {
        decide(ApplicationAccessRequestStatus.APPROVED, actor, decisionReason);
    }

    public void reject(String actor, String decisionReason) {
        decide(ApplicationAccessRequestStatus.REJECTED, actor, decisionReason);
    }

    public void cancel(String actor, String decisionReason) {
        decide(ApplicationAccessRequestStatus.CANCELED, actor, decisionReason);
    }

    private void decide(ApplicationAccessRequestStatus status, String actor, String decisionReason) {
        if (this.status != ApplicationAccessRequestStatus.PENDING) {
            throw new IllegalArgumentException("Application access request is already decided");
        }
        this.status = status;
        this.decidedBy = actor;
        this.decisionReason = decisionReason;
        this.decidedAt = Instant.now();
    }
}
