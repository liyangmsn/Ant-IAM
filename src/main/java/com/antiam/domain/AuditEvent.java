package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "audit_events")
public class AuditEvent extends BaseEntity {

    private String actor;
    private String action;
    private String targetType;
    private String targetId;

    @Column(columnDefinition = "text")
    private String detail;

    public AuditEvent(String actor, String action, String targetType, String targetId, String detail) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }
}
