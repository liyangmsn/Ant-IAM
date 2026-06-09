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
@Table(name = "authentication_sessions")
public class AuthenticationSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    private ApplicationProtocol protocol;

    private String sessionIndex;
    private String ipAddress;
    private String userAgent;
    private Instant expiresAt;
    private Instant endedAt;

    @Column(nullable = false)
    private boolean active;

    public AuthenticationSession(
        UserAccount user,
        Application application,
        ApplicationProtocol protocol,
        String sessionIndex,
        String ipAddress,
        String userAgent,
        Instant expiresAt
    ) {
        this.user = user;
        this.application = application;
        this.protocol = protocol;
        this.sessionIndex = sessionIndex;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    public void end() {
        this.active = false;
        this.endedAt = Instant.now();
    }
}
