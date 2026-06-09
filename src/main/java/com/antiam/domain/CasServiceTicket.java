package com.antiam.domain;

import jakarta.persistence.Column;
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
@Table(name = "cas_service_tickets")
public class CasServiceTicket extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String ticketHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false)
    private String serviceUrl;

    private Instant expiresAt;
    private Instant consumedAt;

    public CasServiceTicket(String ticketHash, Application application, UserAccount user, String serviceUrl, Instant expiresAt) {
        this.ticketHash = ticketHash;
        this.application = application;
        this.user = user;
        this.serviceUrl = serviceUrl;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
