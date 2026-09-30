package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;

import com.antiam.common.AuthenticationFailedException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.SessionRestriction;
import com.antiam.domain.TenantStatus;
import com.antiam.domain.UserAccount;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 门户/控制台登录会话的签发与展示，统一应用会话有效期、记住我和并发会话数设置。
 */
@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private static final int SESSION_TOKEN_BYTES = 32;

    private final AuthenticationSessionRepository sessions;
    private final AuthenticationEventRepository events;
    private final SecuritySettingService securitySettings;
    private final ClientMetadataService clientMetadataService;
    private final TokenSupport tokenSupport;

    /**
     * 返回用户不能登录的原因；可以登录时返回 null。
     */
    public static String signInBlockReason(UserAccount user) {
        if (user.getStatus() != AccountStatus.ACTIVE) {
            return "status=" + user.getStatus();
        }
        if (user.getTenant() != null && user.getTenant().getStatus() == TenantStatus.SUSPENDED) {
            return "tenant_suspended";
        }
        return null;
    }

    /**
     * 校验用户可登录，不可登录时记录失败事件并拒绝。
     */
    public void requireSignInAllowed(UserAccount user, String method, String ipAddress, String userAgent) {
        String reason = signInBlockReason(user);
        if (reason != null) {
            events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.LOGIN_FAILURE, method, ipAddress, userAgent, method + "_login_blocked;" + reason));
            throw new AuthenticationFailedException(user.getStatus() == AccountStatus.LOCKED
                ? "账号已被锁定，请稍后重试或联系管理员解锁"
                : "账号未启用或已被停用，请联系管理员");
        }
    }

    /**
     * 签发登录会话：按记住我选择有效期，超出并发会话上限时结束最早的会话，并记录登录成功事件。
     */
    public AuthenticationSession open(
        UserAccount user,
        ApplicationProtocol protocol,
        String ipAddress,
        String userAgent,
        boolean rememberMe,
        SessionRestriction restriction,
        String method,
        String detail
    ) {
        var settings = securitySettings.general();
        long ttlSeconds = rememberMe ? settings.rememberMeTtlSeconds() : settings.sessionTtlSeconds();
        Instant now = Instant.now();
        enforceConcurrentSessions(user, settings.userConcurrentSessions(), now);
        AuthenticationSession session = new AuthenticationSession(
            user,
            null,
            protocol,
            tokenSupport.generateToken(SESSION_TOKEN_BYTES),
            ipAddress,
            userAgent,
            now.plus(Duration.ofSeconds(Math.max(60, ttlSeconds))));
        if (restriction != null) {
            session.restrict(restriction);
        }
        AuthenticationSession saved = sessions.save(session);
        String eventDetail = detail + (rememberMe ? ";remember_me" : "") + (restriction == null ? "" : ";restriction=" + restriction);
        events.save(new AuthenticationEvent(saved, user, null, AuthenticationEventType.LOGIN_SUCCESS, method, ipAddress, userAgent, eventDetail));
        return saved;
    }

    /**
     * 结束用户除指定会话外的全部活跃会话，返回结束数量。
     */
    public int endOtherSessions(UserAccount user, UUID keepSessionId, String reason) {
        List<AuthenticationSession> others = sessions.findByUserIdAndActive(user.getId(), true).stream()
            .filter(session -> keepSessionId == null || !session.getId().equals(keepSessionId))
            .toList();
        others.forEach(session -> end(session, reason));
        return others.size();
    }

    /**
     * 解除用户当前活跃会话上的指定限制。
     */
    public void clearRestriction(UserAccount user, SessionRestriction restriction) {
        sessions.findByUserIdAndActive(user.getId(), true).stream()
            .filter(session -> session.getRestriction() == restriction)
            .forEach(AuthenticationSession::clearRestriction);
    }

    public AuthenticationSessionResponse toResponse(AuthenticationSession session) {
        return toResponse(session, false);
    }

    /**
     * 转换会话响应；会话索引即访问令牌，只在签发时完整返回，列表中脱敏展示。
     */
    public AuthenticationSessionResponse toResponse(AuthenticationSession session, boolean exposeToken) {
        UUID userId = session.getUser() == null ? null : session.getUser().getId();
        UUID applicationId = session.getApplication() == null ? null : session.getApplication().getId();
        return new AuthenticationSessionResponse(
            session.getId(),
            userId,
            applicationId,
            session.getProtocol(),
            exposeToken ? session.getSessionIndex() : maskSessionIndex(session.getSessionIndex()),
            session.getIpAddress(),
            session.getUserAgent(),
            clientMetadataService.location(session.getIpAddress()),
            clientMetadataService.deviceType(session.getUserAgent()),
            session.getCreatedAt(),
            session.getUpdatedAt(),
            session.getExpiresAt(),
            session.getEndedAt(),
            session.isActive() && (session.getExpiresAt() == null || session.getExpiresAt().isAfter(Instant.now())),
            session.getRestriction());
    }

    private void enforceConcurrentSessions(UserAccount user, int limit, Instant now) {
        if (limit <= 0) {
            return;
        }
        List<AuthenticationSession> active = sessions.findByUserIdAndActive(user.getId(), true).stream()
            .filter(session -> session.getApplication() == null)
            .filter(session -> session.getExpiresAt() == null || session.getExpiresAt().isAfter(now))
            .sorted(Comparator.comparing(AuthenticationSession::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
        int excess = active.size() - limit + 1;
        for (int index = 0; index < excess; index++) {
            end(active.get(index), "concurrent_session_limit");
        }
    }

    private void end(AuthenticationSession session, String reason) {
        session.end();
        events.save(new AuthenticationEvent(
            session,
            session.getUser(),
            session.getApplication(),
            AuthenticationEventType.LOGOUT,
            session.getIpAddress(),
            session.getUserAgent(),
            reason));
    }

    private String maskSessionIndex(String sessionIndex) {
        if (sessionIndex == null || sessionIndex.length() <= 8) {
            return sessionIndex;
        }
        return sessionIndex.substring(0, 6) + "******";
    }
}
