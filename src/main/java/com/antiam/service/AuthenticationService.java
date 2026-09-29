package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.AuthenticationEventResponse;
import static com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationEventRequest;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationSessionRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsResponse;
import static com.antiam.dto.AuthenticationDtos.LoginMfaChallengeResponse;
import static com.antiam.dto.AuthenticationDtos.LoginMfaFactorOption;
import static com.antiam.dto.AuthenticationDtos.MobileLoginResponse;
import static com.antiam.dto.AuthenticationDtos.PasswordLoginResponse;
import static com.antiam.dto.AuthenticationDtos.SendSmsCodeResponse;

import com.antiam.common.AuthenticationFailedException;
import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationMethods;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.CredentialType;
import com.antiam.domain.MfaChallenge;
import com.antiam.domain.MfaChallengeStatus;
import com.antiam.domain.MfaFactor;
import com.antiam.domain.MfaFactorType;
import com.antiam.domain.SessionRestriction;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.MfaChallengeRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserCredentialRepository;
import com.antiam.service.AuthenticationPolicyService.LoginDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String INVALID_CREDENTIALS = "用户名或密码错误";
    private static final Duration LOGIN_MFA_TTL = Duration.ofMinutes(5);
    private static final int MFA_RESEND_INTERVAL_SECONDS = 60;

    private final AuthenticationSessionRepository sessions;
    private final AuthenticationEventRepository events;
    private final UserAccountRepository users;
    private final ApplicationRepository applications;
    private final UserCredentialRepository credentials;
    private final MfaFactorRepository mfaFactors;
    private final MfaChallengeRepository mfaChallenges;
    private final AuditService auditService;
    private final SmsVerificationService smsVerificationService;
    private final MfaVerificationService mfaVerificationService;
    private final LoginSessionService loginSessions;
    private final SecuritySettingService securitySettings;
    private final TokenSupport tokenSupport;
    private final AuthenticationPolicyService authenticationPolicyService;
    private final PasswordEncoder passwordEncoder;

    // 账号不存在时仍执行一次哈希比对，避免通过响应耗时枚举账号。
    private volatile String dummyPasswordHash;

    @Transactional
    // 创建认证会话，记录用户、应用、协议、客户端和过期时间。
    public AuthenticationSessionResponse createSession(CreateAuthenticationSessionRequest request, String actor) {
        UserAccount user = request.userId() == null ? null : getUser(request.userId());
        if (user != null && LoginSessionService.signInBlockReason(user) != null) {
            throw new IllegalArgumentException("用户当前状态不允许登录: " + user.getStatus());
        }
        Application application = request.applicationId() == null ? null : getApplication(request.applicationId());
        String sessionIndex = request.sessionIndex().trim();
        if (sessions.existsBySessionIndex(sessionIndex)) {
            throw new ConflictException("会话索引已存在: " + sessionIndex);
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("会话过期时间必须晚于当前时间");
        }
        AuthenticationSession saved = sessions.save(new AuthenticationSession(
            user,
            application,
            request.protocol(),
            sessionIndex,
            request.ipAddress(),
            request.userAgent(),
            request.expiresAt()));
        auditService.record(actor, "auth_session.create", "auth_session", saved.getId().toString(), request.protocol().name());
        return loginSessions.toResponse(saved, true);
    }

    public SendSmsCodeResponse sendSmsCode(String mobile, String purpose) {
        return smsVerificationService.sendVerificationCode(mobile, purpose);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public MobileLoginResponse mobileLogin(String mobile, String code, boolean rememberMe, String ipAddress, String userAgent) {
        String normalizedMobile = SmsVerificationService.normalizeMobile(mobile);
        smsVerificationService.verify(normalizedMobile, "LOGIN", code);
        List<UserAccount> matched = users.findAllByMobile(normalizedMobile);
        if (matched.isEmpty() && !normalizedMobile.equals(mobile.trim())) {
            matched = users.findAllByMobile(mobile.trim());
        }
        if (matched.size() != 1) {
            // 手机号未绑定或被多个账号重复使用时统一拒绝，避免登录到错误账号。
            throw new AuthenticationFailedException(matched.isEmpty() ? "手机号未绑定账号" : "手机号绑定了多个账号，请使用账号密码登录");
        }
        UserAccount user = matched.getFirst();
        loginSessions.requireSignInAllowed(user, AuthenticationMethods.MOBILE_CODE, ipAddress, userAgent);
        LoginDecision decision = authenticationPolicyService.evaluateLogin(user, ipAddress, userAgent, null);
        if (LoginDecision.DENY.equals(decision.decision())) {
            denyByPolicy(user, AuthenticationMethods.MOBILE_CODE, decision, ipAddress, userAgent);
        }
        if (LoginDecision.MFA_REQUIRED.equals(decision.decision())) {
            LoginMfaChallengeResponse challenge = beginLoginMfa(user, AuthenticationMethods.MOBILE_CODE, decision, ipAddress, userAgent);
            return new MobileLoginResponse(user.getId(), null, true, challenge);
        }
        SessionRestriction restriction = LoginDecision.MFA_ENROLLMENT_REQUIRED.equals(decision.decision())
            ? SessionRestriction.MFA_ENROLLMENT
            : null;
        AuthenticationSession session = loginSessions.open(user, ApplicationProtocol.FORM_FILL, ipAddress, userAgent, rememberMe,
            restriction, AuthenticationMethods.MOBILE_CODE, "mobile_code_login;risk=" + decision.riskLevel());
        auditService.record(user.getUsername(), "mobile_login.success", "user", user.getId().toString(), normalizedMobile);
        return new MobileLoginResponse(user.getId(), loginSessions.toResponse(session, true), false, null);
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public PasswordLoginResponse passwordLogin(String username, String rawPassword, boolean rememberMe, String ipAddress, String userAgent) {
        String normalizedUsername = username == null ? "" : username.trim();
        UserAccount user = users.findByUsername(normalizedUsername).orElse(null);
        UserCredential credential = user == null ? null : credentials.findByUserAndType(user, CredentialType.PASSWORD).orElse(null);
        if (user == null || credential == null) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash());
            if (user != null) {
                events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.LOGIN_FAILURE, AuthenticationMethods.PASSWORD, ipAddress, userAgent, "password_login_failed;no_password_credential"));
            }
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        var general = securitySettings.general();
        autoUnlock(user, credential, general.autoUnlockMinutes());
        if (!passwordEncoder.matches(rawPassword, credential.getSecretHash())) {
            credential.markFailed(Duration.ofMinutes(Math.max(1, general.loginFailureWindowMinutes())));
            int maxAttempts = Math.max(1, general.loginFailureMaxAttempts());
            boolean locked = user.getStatus() == AccountStatus.ACTIVE && credential.getFailedAttempts() >= maxAttempts;
            if (locked) {
                user.lock();
                credential.markLocked();
                loginSessions.endOtherSessions(user, null, "account_locked");
            }
            events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.LOGIN_FAILURE, AuthenticationMethods.PASSWORD, ipAddress, userAgent,
                "password_login_failed;failed_attempts=" + credential.getFailedAttempts() + ";locked=" + locked));
            if (locked) {
                auditService.record(normalizedUsername, "user.password.lock", "user", user.getId().toString(), "failed_attempts=" + credential.getFailedAttempts());
            }
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        loginSessions.requireSignInAllowed(user, AuthenticationMethods.PASSWORD, ipAddress, userAgent);
        credential.markUsed();
        boolean expired = credential.isExpired(Instant.now());
        boolean changeRequired = credential.isTemporary() || expired;
        LoginDecision decision = authenticationPolicyService.evaluateLogin(user, ipAddress, userAgent, null);
        if (LoginDecision.DENY.equals(decision.decision())) {
            denyByPolicy(user, AuthenticationMethods.PASSWORD, decision, ipAddress, userAgent);
        }
        if (LoginDecision.MFA_REQUIRED.equals(decision.decision())) {
            LoginMfaChallengeResponse challenge = beginLoginMfa(user, AuthenticationMethods.PASSWORD, decision, ipAddress, userAgent);
            return new PasswordLoginResponse(user.getId(), null, credential.isTemporary(), expired, changeRequired, true, challenge, null);
        }
        SessionRestriction restriction = changeRequired ? SessionRestriction.PASSWORD_CHANGE
            : LoginDecision.MFA_ENROLLMENT_REQUIRED.equals(decision.decision()) ? SessionRestriction.MFA_ENROLLMENT
            : null;
        AuthenticationSession session = loginSessions.open(user, ApplicationProtocol.FORM_FILL, ipAddress, userAgent, rememberMe,
            restriction, AuthenticationMethods.PASSWORD, (expired ? "password_login;password_expired" : "password_login") + ";risk=" + decision.riskLevel());
        auditService.record(normalizedUsername, "password_login.success", "user", user.getId().toString(), normalizedUsername);
        return new PasswordLoginResponse(user.getId(), loginSessions.toResponse(session, true), credential.isTemporary(), expired, changeRequired, false, null,
            passwordExpiryReminder(credential));
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    // 完成登录 MFA 二次验证并签发会话。
    public PasswordLoginResponse mfaLogin(String challengeId, String code, boolean recoveryCode, boolean rememberMe, String ipAddress, String userAgent) {
        MfaChallenge challenge = loginChallenge(challengeId);
        UserAccount user = challenge.getUser();
        loginSessions.requireSignInAllowed(user, AuthenticationMethods.MFA, ipAddress, userAgent);
        boolean verified;
        String factorLabel;
        if (recoveryCode) {
            MfaFactor recovery = mfaFactors.findByUserIdAndType(user.getId(), MfaFactorType.RECOVERY_CODE)
                .filter(MfaVerificationService::hasRecoveryCodes)
                .orElse(null);
            verified = recovery != null && mfaVerificationService.consumeRecoveryCode(recovery, code);
            factorLabel = MfaFactorType.RECOVERY_CODE.name();
        } else {
            verified = mfaVerificationService.verify(challenge, code);
            factorLabel = challenge.getFactor().getType().name();
        }
        if (!verified) {
            challenge.fail();
            events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.MFA_FAILURE, AuthenticationMethods.MFA, ipAddress, userAgent,
                "mfa_login_failed;factor=" + factorLabel + ";attempts=" + challenge.getAttempts()));
            if (challenge.getStatus() == MfaChallengeStatus.FAILED) {
                throw new AuthenticationFailedException("验证码错误次数过多，请重新登录");
            }
            throw new AuthenticationFailedException("验证码不正确");
        }
        challenge.verify();
        events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.MFA_SUCCESS, AuthenticationMethods.MFA, ipAddress, userAgent,
            "mfa_login;factor=" + factorLabel));
        UserCredential credential = credentials.findByUserAndType(user, CredentialType.PASSWORD).orElse(null);
        boolean expired = credential != null && credential.isExpired(Instant.now());
        boolean temporary = credential != null && credential.isTemporary();
        AuthenticationSession session = loginSessions.open(user, ApplicationProtocol.FORM_FILL, ipAddress, userAgent, rememberMe,
            temporary || expired ? SessionRestriction.PASSWORD_CHANGE : null,
            AuthenticationMethods.MFA, "mfa_login;factor=" + factorLabel + (expired ? ";password_expired" : ""));
        auditService.record(user.getUsername(), "mfa_login.success", "user", user.getId().toString(), user.getUsername() + ";mfa=" + factorLabel);
        return new PasswordLoginResponse(user.getId(), loginSessions.toResponse(session, true), temporary, expired, temporary || expired, false, null,
            passwordExpiryReminder(credential));
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    // 切换登录 MFA 因子或重新发送验证码，沿用原挑战的失败次数。
    public LoginMfaChallengeResponse switchMfaFactor(String challengeId, UUID factorId) {
        MfaChallenge previous = loginChallenge(challengeId);
        MfaFactor target = mfaFactors.findById(factorId)
            .filter(factor -> factor.getUser().getId().equals(previous.getUser().getId()))
            .filter(MfaVerificationService::isLoginCapable)
            .orElseThrow(() -> new IllegalArgumentException("所选 MFA 因子不可用"));
        boolean resend = target.getId().equals(previous.getFactor().getId());
        boolean delivered = target.getType() == MfaFactorType.SMS || target.getType() == MfaFactorType.EMAIL;
        if (resend && delivered && previous.getCreatedAt() != null
            && previous.getCreatedAt().plusSeconds(MFA_RESEND_INTERVAL_SECONDS).isAfter(Instant.now())) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }
        LoginMfaChallengeResponse response = startLoginChallenge(previous.getUser(), target, previous.getAttempts());
        previous.expire();
        return response;
    }

    @Transactional
    // 结束指定认证会话，并补写登出事件。
    public void endSession(UUID sessionId, String actor) {
        AuthenticationSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Authentication session not found: " + sessionId));
        endSession(session, actor, "Session ended by administrator");
        auditService.record(actor, "auth_session.end", "auth_session", sessionId.toString(), session.getSessionIndex());
    }

    @Transactional
    // 用户主动登出：结束当前会话令牌，使其在服务端立即失效。
    public void logout(UUID sessionId, String actor) {
        AuthenticationSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Authentication session not found: " + sessionId));
        endSession(session, actor, "User signed out");
        auditService.record(actor, "auth_session.logout", "auth_session", sessionId.toString(), null);
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
        return loginSessions.toResponse(session);
    }

    private MfaChallenge loginChallenge(String challengeId) {
        MfaChallenge challenge = mfaChallenges.findByChallengeId(challengeId == null ? "" : challengeId.trim())
            .filter(value -> MfaChallenge.PURPOSE_LOGIN.equals(value.getPurpose()))
            .orElseThrow(() -> new AuthenticationFailedException("MFA 验证已失效，请重新登录"));
        if (!challenge.isUsable(Instant.now())) {
            if (challenge.getStatus() == MfaChallengeStatus.PENDING) {
                challenge.expire();
            }
            throw new AuthenticationFailedException("MFA 验证已失效，请重新登录");
        }
        return challenge;
    }

    // 创建登录 MFA 挑战；未指定因子时按 TOTP、短信、邮件顺序选择，投递失败时尝试下一个因子。
    /**
     * 第一因子已通过但登录策略要求二次验证时，发起登录 MFA 挑战并记录事件。
     */
    @Transactional
    public LoginMfaChallengeResponse beginLoginMfa(UserAccount user, String method, LoginDecision decision, String ipAddress, String userAgent) {
        LoginMfaChallengeResponse challenge = startLoginChallenge(user, null, 0);
        events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.MFA_CHALLENGE, method, ipAddress, userAgent,
            method + "_verified;mfa_required;factor=" + challenge.factorType() + ";risk=" + decision.riskLevel()));
        return challenge;
    }

    private LoginMfaChallengeResponse startLoginChallenge(UserAccount user, MfaFactor preferred, int inheritedAttempts) {
        List<MfaFactor> available = mfaFactors.findByUserId(user.getId()).stream()
            .filter(MfaVerificationService::isLoginCapable)
            .sorted(Comparator.comparingInt(this::factorOrder).thenComparing(MfaFactor::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        if (available.isEmpty()) {
            throw new AuthenticationFailedException("没有可用的 MFA 因子");
        }
        boolean recoveryAllowed = mfaFactors.findByUserIdAndType(user.getId(), MfaFactorType.RECOVERY_CODE)
            .filter(MfaVerificationService::hasRecoveryCodes)
            .isPresent();
        List<MfaFactor> candidates = preferred == null ? available : List.of(preferred);
        RuntimeException lastFailure = null;
        for (MfaFactor factor : candidates) {
            MfaVerificationService.IssuedCode issued;
            try {
                issued = mfaVerificationService.issueCode(user, factor);
            } catch (RuntimeException ex) {
                lastFailure = ex;
                continue;
            }
            MfaChallenge challenge = new MfaChallenge(
                user,
                factor,
                tokenSupport.generateToken(24),
                issued.codeHash(),
                Instant.now().plus(challengeTtl(factor)),
                MfaChallenge.PURPOSE_LOGIN);
            challenge.inheritAttempts(inheritedAttempts);
            MfaChallenge saved = mfaChallenges.save(challenge);
            return new LoginMfaChallengeResponse(
                saved.getChallengeId(),
                factor.getId(),
                factor.getType(),
                mfaVerificationService.deliveryHint(factor),
                saved.getExpiresAt(),
                issued.echoCode(),
                available.stream().map(item -> new LoginMfaFactorOption(item.getId(), item.getType(), item.getName())).toList(),
                recoveryAllowed);
        }
        throw lastFailure;
    }

    private Duration challengeTtl(MfaFactor factor) {
        if (factor.getType() == MfaFactorType.SMS) {
            return Duration.ofSeconds(Math.max(60, smsVerificationService.codeTtlSeconds()));
        }
        return LOGIN_MFA_TTL;
    }

    private int factorOrder(MfaFactor factor) {
        return switch (factor.getType()) {
            case TOTP -> 0;
            case SMS -> 1;
            case EMAIL -> 2;
            case WEBAUTHN -> 3;
            case RECOVERY_CODE -> 4;
        };
    }

    private void denyByPolicy(UserAccount user, String method, LoginDecision decision, String ipAddress, String userAgent) {
        events.save(new AuthenticationEvent(null, user, null, AuthenticationEventType.LOGIN_FAILURE, method, ipAddress, userAgent,
            method + "_login_denied;risk=" + decision.riskLevel()));
        throw new AuthenticationFailedException("当前登录环境存在风险，已拒绝登录");
    }

    // 密码进入过期提醒期时返回剩余天数（向上取整），供登录页提示用户尽快修改密码。
    private Integer passwordExpiryReminder(UserCredential credential) {
        if (credential == null || credential.getExpiresAt() == null) {
            return null;
        }
        Instant now = Instant.now();
        if (!credential.getExpiresAt().isAfter(now)) {
            return null;
        }
        int reminderDays = securitySettings.passwordPolicy().expiryReminderDays();
        if (reminderDays <= 0) {
            return null;
        }
        long seconds = Duration.between(now, credential.getExpiresAt()).getSeconds();
        long days = (seconds + 86_399) / 86_400;
        return days <= reminderDays ? (int) days : null;
    }

    // 密码错误锁定的账号在达到自动解锁时间后恢复可用；管理员手动锁定的账号不会自动解锁。
    private void autoUnlock(UserAccount user, UserCredential credential, int autoUnlockMinutes) {
        if (user.getStatus() != AccountStatus.LOCKED || credential.getLockedAt() == null || autoUnlockMinutes <= 0) {
            return;
        }
        if (credential.getLockedAt().plus(Duration.ofMinutes(autoUnlockMinutes)).isAfter(Instant.now())) {
            return;
        }
        user.activate();
        credential.resetFailures();
        auditService.record("system", "user.password.auto_unlock", "user", user.getId().toString(), user.getUsername());
    }

    private String dummyPasswordHash() {
        String hash = dummyPasswordHash;
        if (hash == null) {
            hash = passwordEncoder.encode(tokenSupport.generateToken(16));
            dummyPasswordHash = hash;
        }
        return hash;
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
            || (event.getUser() != null && (contains(event.getUser().getUsername(), keyword) || contains(event.getUser().getDisplayName(), keyword)))
            || contains(event.getUserAgent(), keyword)
            || contains(event.getIpAddress(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
