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
@Table(name = "authentication_events")
public class AuthenticationEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private AuthenticationSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    private AuthenticationEventType type;

    private String method;

    private String ipAddress;
    private String userAgent;

    @Column(columnDefinition = "text")
    private String detail;

    public AuthenticationEvent(
        AuthenticationSession session,
        UserAccount user,
        Application application,
        AuthenticationEventType type,
        String ipAddress,
        String userAgent,
        String detail
    ) {
        this(session, user, application, type, null, ipAddress, userAgent, detail);
    }

    public AuthenticationEvent(
        AuthenticationSession session,
        UserAccount user,
        Application application,
        AuthenticationEventType type,
        String method,
        String ipAddress,
        String userAgent,
        String detail
    ) {
        this.session = session;
        this.user = user;
        this.application = application;
        this.type = type;
        this.method = method;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.detail = detail;
    }
}
