package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.AuthenticationEventResponse;
import static com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationEventRequest;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationSessionRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsResponse;

import com.antiam.common.NotFoundException;
import com.antiam.domain.Application;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.UserAccount;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationSessionRepository sessions;
    private final AuthenticationEventRepository events;
    private final UserAccountRepository users;
    private final ApplicationRepository applications;
    private final AuditService auditService;

    @Transactional
    // 创建认证会话，记录用户、应用、协议、客户端和过期时间。
    public AuthenticationSessionResponse createSession(CreateAuthenticationSessionRequest request, String actor) {
        UserAccount user = request.userId() == null ? null : getUser(request.userId());
        Application application = request.applicationId() == null ? null : getApplication(request.applicationId());
        AuthenticationSession saved = sessions.save(new AuthenticationSession(
            user,
            application,
            request.protocol(),
            request.sessionIndex(),
            request.ipAddress(),
            request.userAgent(),
            request.expiresAt()));
        auditService.record(actor, "auth_session.create", "auth_session", saved.getId().toString(), request.protocol().name());
        return toResponse(saved);
    }

    @Transactional
    // 结束指定认证会话，并补写登出事件。
    public void endSession(UUID sessionId, String actor) {
        AuthenticationSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Authentication session not found: " + sessionId));
        endSession(session, actor, "Session ended by administrator");
        auditService.record(actor, "auth_session.end", "auth_session", sessionId.toString(), session.getSessionIndex());
    }

    @Transactional(readOnly = true)
    // 查询活跃认证会话，常用于用户离职、锁定和应用下线前的清理判断。
    public List<AuthenticationSessionResponse> listActiveSessions(UUID userId, UUID applicationId) {
        return listSessions(userId, applicationId, true);
    }

    @Transactional(readOnly = true)
    // 按用户、应用和活跃状态查询认证会话。
    public List<AuthenticationSessionResponse> listSessions(UUID userId, UUID applicationId, Boolean active) {
        boolean activeOnly = active == null || active;
        if (userId != null && applicationId != null) {
            return sessions.findByUserIdAndApplicationIdAndActive(userId, applicationId, activeOnly).stream()
                .map(this::toResponse)
                .toList();
        }
        if (userId != null) {
            return sessions.findByUserIdAndActive(userId, activeOnly).stream().map(this::toResponse).toList();
        }
        if (applicationId != null) {
            return sessions.findByApplicationIdAndActive(applicationId, activeOnly).stream().map(this::toResponse).toList();
        }
        return sessions.findByActive(activeOnly).stream().map(this::toResponse).toList();
    }

    @Transactional
    // 批量结束认证会话，支持按用户、应用或全量活跃会话处理。
    public EndAuthenticationSessionsResponse endSessions(EndAuthenticationSessionsRequest request, String actor) {
        boolean all = Boolean.TRUE.equals(request.all());
        if (!all && request.userId() == null && request.applicationId() == null) {
            throw new IllegalArgumentException("Set userId, applicationId, or all=true to end sessions");
        }
        List<AuthenticationSession> activeSessions = findActiveSessionsForEnd(request);
        activeSessions.forEach(session -> endSession(session, actor, request.detail()));
        auditService.record(actor, "auth_session.bulk_end", "auth_session", "*", "ended=" + activeSessions.size());
        return new EndAuthenticationSessionsResponse(activeSessions.size());
    }

    @Transactional
    // 写入认证事件，用于审计、风险分析和协议登录追踪。
    public AuthenticationEventResponse recordEvent(CreateAuthenticationEventRequest request, String actor) {
        AuthenticationSession session = request.sessionId() == null ? null : sessions.findById(request.sessionId())
            .orElseThrow(() -> new NotFoundException("Authentication session not found: " + request.sessionId()));
        UserAccount user = request.userId() == null ? null : getUser(request.userId());
        Application application = request.applicationId() == null ? null : getApplication(request.applicationId());
        AuthenticationEvent saved = events.save(new AuthenticationEvent(
            session,
            user,
            application,
            request.type(),
            request.ipAddress(),
            request.userAgent(),
            request.detail()));
        auditService.record(actor, "auth_event.record", "auth_event", saved.getId().toString(), request.type().name());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询指定类型的最近认证事件。
    public List<AuthenticationEventResponse> recentEvents(AuthenticationEventType type) {
        return recentEvents(type, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    // 按类型、用户、应用、会话、IP 和关键字查询最近认证事件。
    public List<AuthenticationEventResponse> recentEvents(
        AuthenticationEventType type,
        UUID userId,
        UUID applicationId,
        UUID sessionId,
        String ipAddress,
        String keyword
    ) {
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase();
        List<AuthenticationEvent> values = type == null
            ? events.findTop100ByOrderByCreatedAtDesc()
            : events.findTop100ByTypeOrderByCreatedAtDesc(type);
        return values.stream()
            .filter(event -> userId == null || (event.getUser() != null && event.getUser().getId().equals(userId)))
            .filter(event -> applicationId == null || (event.getApplication() != null && event.getApplication().getId().equals(applicationId)))
            .filter(event -> sessionId == null || (event.getSession() != null && event.getSession().getId().equals(sessionId)))
            .filter(event -> ipAddress == null || ipAddress.equals(event.getIpAddress()))
            .filter(event -> matchesKeyword(event, normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    private UserAccount getUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private Application getApplication(UUID applicationId) {
        return applications.findById(applicationId)
            .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));
    }

    private List<AuthenticationSession> findActiveSessionsForEnd(EndAuthenticationSessionsRequest request) {
        if (request.userId() != null && request.applicationId() != null) {
            return sessions.findByUserIdAndApplicationIdAndActive(request.userId(), request.applicationId(), true);
        }
        if (request.userId() != null) {
            return sessions.findByUserIdAndActive(request.userId(), true);
        }
        if (request.applicationId() != null) {
            return sessions.findByApplicationIdAndActive(request.applicationId(), true);
        }
        return sessions.findByActive(true);
    }

    private void endSession(AuthenticationSession session, String actor, String detail) {
        if (!session.isActive()) {
            return;
        }
        session.end();
        events.save(new AuthenticationEvent(
            session,
            session.getUser(),
            session.getApplication(),
            AuthenticationEventType.LOGOUT,
            session.getIpAddress(),
            session.getUserAgent(),
            detail == null || detail.isBlank() ? "Session ended by " + actor : detail));
    }

    private AuthenticationSessionResponse toResponse(AuthenticationSession session) {
        UUID userId = session.getUser() == null ? null : session.getUser().getId();
        UUID applicationId = session.getApplication() == null ? null : session.getApplication().getId();
        return new AuthenticationSessionResponse(
            session.getId(),
            userId,
            applicationId,
            session.getProtocol(),
            session.getSessionIndex(),
            session.getIpAddress(),
            session.getUserAgent(),
            session.getExpiresAt(),
            session.getEndedAt(),
            session.isActive());
    }

    private AuthenticationEventResponse toResponse(AuthenticationEvent event) {
        UUID sessionId = event.getSession() == null ? null : event.getSession().getId();
        UUID userId = event.getUser() == null ? null : event.getUser().getId();
        UUID applicationId = event.getApplication() == null ? null : event.getApplication().getId();
        return new AuthenticationEventResponse(
            event.getId(),
            sessionId,
            userId,
            applicationId,
            event.getType(),
            event.getIpAddress(),
            event.getUserAgent(),
            event.getDetail(),
            event.getCreatedAt());
    }

    private boolean matchesKeyword(AuthenticationEvent event, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(event.getDetail(), keyword)
            || contains(event.getUserAgent(), keyword)
            || contains(event.getIpAddress(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
