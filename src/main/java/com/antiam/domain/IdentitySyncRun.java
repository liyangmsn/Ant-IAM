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
@Table(name = "identity_sync_runs")
public class IdentitySyncRun extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_job_id", nullable = false)
    private IdentitySyncJob syncJob;

    @Enumerated(EnumType.STRING)
    private IdentitySyncRunStatus status;

    private Instant startedAt;
    private Instant finishedAt;
    private int usersCreated;
    private int usersUpdated;
    private int groupsCreated;
    private int groupsUpdated;

    @Column(columnDefinition = "text")
    private String message;

    public IdentitySyncRun(IdentitySyncJob syncJob) {
        this.syncJob = syncJob;
        this.status = IdentitySyncRunStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void success(int usersCreated, int usersUpdated, int groupsCreated, int groupsUpdated, String message) {
        this.status = IdentitySyncRunStatus.SUCCESS;
        this.usersCreated = usersCreated;
        this.usersUpdated = usersUpdated;
        this.groupsCreated = groupsCreated;
        this.groupsUpdated = groupsUpdated;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = IdentitySyncRunStatus.FAILED;
        this.message = message;
        this.finishedAt = Instant.now();
    }
}
