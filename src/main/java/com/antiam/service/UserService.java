package com.antiam.service;

import static com.antiam.dto.UserDtos.ChangeOwnPasswordRequest;
import static com.antiam.dto.UserDtos.BindMobileRequest;
import static com.antiam.dto.UserDtos.CreateUserRequest;
import static com.antiam.dto.UserDtos.EffectivePermissionResponse;
import static com.antiam.dto.UserDtos.ConsumePasswordResetTicketRequest;
import static com.antiam.dto.UserDtos.CreatePasswordResetTicketRequest;
import static com.antiam.dto.UserDtos.MfaChallengeDetailResponse;
import static com.antiam.dto.UserDtos.MfaChallengeResponse;
import static com.antiam.dto.UserDtos.MfaFactorResponse;
import static com.antiam.dto.UserDtos.PasswordResetTicketDetailResponse;
import static com.antiam.dto.UserDtos.PasswordResetTicketResponse;
import static com.antiam.dto.UserDtos.RegisterMfaFactorRequest;
import static com.antiam.dto.UserDtos.RecoveryCodesResponse;
import static com.antiam.dto.UserDtos.SetPasswordRequest;
import static com.antiam.dto.UserDtos.StartMfaChallengeRequest;
import static com.antiam.dto.UserDtos.UpdateMfaFactorRequest;
import static com.antiam.dto.UserDtos.UpdateUserRequest;
import static com.antiam.dto.UserDtos.UserEffectiveAccessResponse;
import static com.antiam.dto.UserDtos.UserResponse;
import static com.antiam.dto.UserDtos.VerifyMfaChallengeRequest;
import static com.antiam.dto.UserDtos.VerifyMfaChallengeResponse;
import static com.antiam.dto.UserDtos.VerifyPasswordResponse;

import com.antiam.common.AuthenticationFailedException;
import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
import com.antiam.config.SecurityAuthorities;
import com.antiam.domain.AccountStatus;
import com.antiam.domain.ApplicationAssignment;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.CredentialType;
import com.antiam.domain.MfaChallenge;
import com.antiam.domain.MfaChallengeStatus;
import com.antiam.domain.MfaFactor;
import com.antiam.domain.MfaFactorType;
import com.antiam.domain.OAuthAccessToken;
import com.antiam.domain.OAuthRefreshToken;
import com.antiam.domain.Organization;
import com.antiam.domain.PasswordResetTicket;
import com.antiam.domain.SessionRestriction;
import com.antiam.domain.Permission;
import com.antiam.domain.Role;
import com.antiam.domain.Tenant;
import com.antiam.domain.TenantStatus;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserCredential;
import com.antiam.domain.UserCredentialHistory;
import com.antiam.domain.UserGroup;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.ApplicationAssignmentRepository;
import com.antiam.repository.MfaChallengeRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.OAuthAccessTokenRepository;
import com.antiam.repository.OAuthRefreshTokenRepository;
import com.antiam.repository.PasswordResetTicketRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserCredentialHistoryRepository;
import com.antiam.repository.UserCredentialRepository;
import com.antiam.repository.UserGroupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int DEFAULT_PASSWORD_RESET_TICKET_MINUTES = 30;
    private static final int MAX_PASSWORD_RESET_TICKET_MINUTES = 7 * 24 * 60;

    private final UserAccountRepository users;
    private final ApplicationAssignmentRepository applicationAssignments;
    private final UserGroupRepository groups;
    private final RoleRepository roles;
    private final UserCredentialRepository credentials;
    private final UserCredentialHistoryRepository credentialHistories;
    private final PasswordResetTicketRepository passwordResetTickets;
    private final MfaFactorRepository mfaFactors;
    private final MfaChallengeRepository mfaChallenges;
    private final AuthenticationEventRepository authenticationEvents;
    private final AuthenticationSessionRepository authenticationSessions;
    private final OAuthAccessTokenRepository oauthAccessTokens;
    private final OAuthRefreshTokenRepository oauthRefreshTokens;
    private final OrganizationService organizationService;
    private final TenantService tenantService;
    private final AuthenticationPolicyService authenticationPolicyService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final TokenSupport tokens;
    private final SmsVerificationService smsVerificationService;
    private final MailDeliveryService mailDeliveryService;
    private final MfaVerificationService mfaVerification;
    private final LoginSessionService loginSessions;
    private final SecuritySettingService securitySettings;

    /**
     * 创建内部用户账号，并在提供初始密码时创建临时密码凭据。
     */
    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        Organization organization = request.organizationId() == null ? null : organizationService.getEntity(request.organizationId());
        Tenant tenant = request.tenantId() == null ? null : tenantService.getEntity(request.tenantId());
        if (tenant != null && tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new IllegalArgumentException("租户已暂停，不能创建用户");
        }
        String username = requireUniqueUsername(request.username());
        UserAccount saved = users.save(new UserAccount(
            username,
            request.displayName(),
            request.email(),
            uniqueMobile(request.mobile(), null),
            tenant,
            organization));
        if ("admin".equals(request.userType())) {
            Role adminRole = roles.findByCode(SecurityAuthorities.IAM_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("IAM admin role is not initialized"));
            saved.grant(adminRole);
        }
        if (request.initialPassword() != null && !request.initialPassword().isBlank()) {
            validatePasswordPolicy(saved, request.initialPassword());
            credentials.save(new UserCredential(
                saved,
                CredentialType.PASSWORD,
                passwordEncoder.encode(request.initialPassword()),
                true,
                passwordExpiresAt()));
        }
        auditService.record(actor, "user.create", "user", saved.getId().toString(), saved.getUsername());
        return toResponse(saved);
    }

    /**
     * 通过 SCIM 创建用户账号，适用于外部身份源写入用户。
     */
    @Transactional
    public UserResponse createScimUser(String username, String displayName, String email, String mobile, String actor) {
        UserAccount saved = users.save(new UserAccount(
            requireUniqueUsername(username), displayName, email, uniqueMobile(mobile, null), null, null));
        auditService.record(actor, "scim.user.create", "user", saved.getId().toString(), saved.getUsername());
        return toResponse(saved);
    }

    /**
     * 通过 SCIM PUT/PATCH 更新用户资料和启用状态；active 为空时不改变账号状态，组织归属保持不变。
     */
    @Transactional
    public UserResponse updateScimUser(UUID userId, String displayName, String email, String mobile, Boolean active, String actor) {
        UserAccount user = getEntity(userId);
        String name = displayName == null || displayName.isBlank() ? user.getUsername() : displayName;
        user.updateProfile(name, email, uniqueMobile(mobile, userId), user.getOrganization());
        auditService.record(actor, "scim.user.update", "user", userId.toString(), user.getUsername());
        if (Boolean.TRUE.equals(active) && user.getStatus() != AccountStatus.ACTIVE) {
            return activate(userId, actor);
        }
        if (Boolean.FALSE.equals(active) && user.getStatus() == AccountStatus.ACTIVE) {
            return suspend(userId, actor);
        }
        return toResponse(user);
    }

    /**
     * 更新用户基础资料和所属组织。
     */
    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request, String actor) {
        UserAccount user = getEntity(userId);
        Organization organization = request.organizationId() == null ? null : organizationService.getEntity(request.organizationId());
        user.updateProfile(request.displayName(), request.email(), uniqueMobile(request.mobile(), userId), organization);
        auditService.record(actor, "user.update", "user", userId.toString(), user.getUsername());
        return toResponse(user);
    }

    /**
     * 激活用户并重置密码失败计数。
     */
    @Transactional
    public UserResponse activate(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        user.activate();
        credentials.findByUserAndType(user, CredentialType.PASSWORD).ifPresent(UserCredential::resetFailures);
        auditService.record(actor, "user.activate", "user", userId.toString(), user.getUsername());
        return toResponse(user);
    }

    /**
     * 暂停用户，并回收直接应用授权、活跃会话和 OAuth token。
     */
    @Transactional
    public UserResponse suspend(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        user.suspend();
        disableDirectApplicationAssignments(user, actor, "user.suspend");
        endActiveSessions(user, actor, "user.suspend");
        revokeOAuthTokens(user, actor, "user.suspend");
        auditService.record(actor, "user.suspend", "user", userId.toString(), user.getUsername());
        return toResponse(user);
    }

    /**
     * 锁定用户，并回收直接应用授权、活跃会话和 OAuth token。
     */
    @Transactional
    public UserResponse lock(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        user.lock();
        disableDirectApplicationAssignments(user, actor, "user.lock");
        endActiveSessions(user, actor, "user.lock");
        revokeOAuthTokens(user, actor, "user.lock");
        auditService.record(actor, "user.lock", "user", userId.toString(), user.getUsername());
        return toResponse(user);
    }

    /**
     * 将用户标记为离职，并执行访问回收。
     */
    @Transactional
    public UserResponse depart(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        user.depart();
        disableDirectApplicationAssignments(user, actor, "user.depart");
        endActiveSessions(user, actor, "user.depart");
        revokeOAuthTokens(user, actor, "user.depart");
        auditService.record(actor, "user.depart", "user", userId.toString(), user.getUsername());
        return toResponse(user);
    }

    /**
     * 删除用户账号：先结束活跃会话；凭据、MFA、令牌、授权等关联数据由数据库级联清理，认证事件保留但解除关联。
     */
    @Transactional
    public void delete(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        if (user.getUsername().equals(actor)) {
            throw new IllegalArgumentException("不能删除当前登录的账号");
        }
        // 会话与认证事件的 user_id 由数据库外键置空，保留历史记录。
        authenticationSessions.endActiveByUserId(userId, Instant.now());
        users.delete(user);
        auditService.record(actor, "user.delete", "user", userId.toString(), user.getUsername());
    }

    /**
     * 管理员设置用户密码，并应用当前密码策略。
     */
    @Transactional
    public void setPassword(UUID userId, SetPasswordRequest request, String actor) {
        UserAccount user = getEntity(userId);
        rotatePassword(user, request.password(), Boolean.TRUE.equals(request.temporary()));
        endActiveSessions(user, actor, "user.password.set");
        revokeOAuthTokens(user, actor, "user.password.set");
        auditService.record(actor, "user.password.set", "user", userId.toString(), user.getUsername());
    }

    /**
     * 当前用户自助修改密码，会校验当前密码并记录认证事件。
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public void changeOwnPassword(String username, ChangeOwnPasswordRequest request) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        UserCredential credential = credentials.findByUserAndType(user, CredentialType.PASSWORD)
            .orElseThrow(() -> new NotFoundException("Password credential not found for user: " + username));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("User account is not active");
        }
        if (!passwordEncoder.matches(request.currentPassword(), credential.getSecretHash())) {
            boolean locked = recordPasswordFailure(user, credential);
            if (locked) {
                endActiveSessions(user, username, "user.password.lock");
            }
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                null,
                AuthenticationEventType.LOGIN_FAILURE,
                null,
                null,
                "password_change_current_password_invalid;failed_attempts=" + credential.getFailedAttempts() + ";locked=" + locked));
            if (locked) {
                auditService.record(username, "user.password.lock", "user", user.getId().toString(), "failed_attempts=" + credential.getFailedAttempts());
            }
            throw new AuthenticationFailedException(locked ? "当前密码错误次数过多，账号已锁定" : "当前密码不正确");
        }
        rotatePassword(user, request.newPassword(), false);
        int ended = loginSessions.endOtherSessions(user, currentSessionId(), "password_changed");
        loginSessions.clearRestriction(user, SessionRestriction.PASSWORD_CHANGE);
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            null,
            AuthenticationEventType.LOGIN_SUCCESS,
            null,
            null,
            "password_changed;other_sessions_ended=" + ended));
        auditService.record(username, "user.password.change", "user", user.getId().toString(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public void sendMobileBindingCode(UUID userId, String mobile) {
        getEntity(userId);
        String normalized = SmsVerificationService.normalizeMobile(mobile);
        if (users.existsByMobileAndIdNot(normalized, userId)) {
            throw new ConflictException("该手机号已绑定其他用户");
        }
        smsVerificationService.sendVerificationCode(normalized, "BIND_MOBILE");
    }

    @Transactional
    public UserResponse bindMobile(UUID userId, BindMobileRequest request, String actor) {
        UserAccount user = getEntity(userId);
        String mobile = SmsVerificationService.normalizeMobile(request.mobile());
        smsVerificationService.verify(mobile, "BIND_MOBILE", request.code());
        if (users.existsByMobileAndIdNot(mobile, userId)) {
            throw new ConflictException("该手机号已绑定其他用户");
        }
        user.updateProfile(user.getDisplayName(), user.getEmail(), mobile, user.getOrganization());
        auditService.record(actor, "user.mobile.bind", "user", userId.toString(), mobile);
        return toResponse(user);
    }

    /**
     * 更新当前用户头像地址。
     */
    @Transactional
    public UserResponse changeOwnAvatar(String username, String avatarUrl) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        user.changeAvatar(avatarUrl);
        auditService.record(username, "user.avatar.update", "user", user.getId().toString(), avatarUrl);
        return toResponse(user);
    }

    /**
     * 创建一次性密码重置票据，仅在创建响应中返回明文 resetToken。
     */
    @Transactional
    public PasswordResetTicketResponse createPasswordResetTicket(CreatePasswordResetTicketRequest request, String actor) {
        UserAccount user = getEntity(request.userId());
        requireResettable(user);
        String resetToken = tokens.generateToken(32);
        PasswordResetTicket saved = passwordResetTickets.save(new PasswordResetTicket(
            user,
            tokens.sha256(resetToken),
            Instant.now().plus(Duration.ofMinutes(normalizeResetTicketMinutes(request.expiresInMinutes()))),
            actor));
        auditService.record(actor, "user.password_reset_ticket.create", "user", user.getId().toString(), user.getUsername());
        return toResponse(saved, resetToken);
    }

    /**
     * 消费密码重置票据并设置新密码，成功后票据立即失效。
     */
    @Transactional
    public void consumePasswordResetTicket(ConsumePasswordResetTicketRequest request) {
        PasswordResetTicket ticket = passwordResetTickets.findByTokenHash(tokens.sha256(request.resetToken()))
            .orElseThrow(() -> new NotFoundException("Password reset ticket not found"));
        if (!ticket.isUsable(Instant.now())) {
            throw new IllegalArgumentException("Password reset ticket is expired or already consumed");
        }
        UserAccount user = ticket.getUser();
        requireResettable(user);
        rotatePassword(user, request.newPassword(), Boolean.TRUE.equals(request.temporary()));
        ticket.consume();
        endActiveSessions(user, "password-reset-ticket", "user.password_reset");
        revokeOAuthTokens(user, "password-reset-ticket", "user.password_reset");
        auditService.record("password-reset-ticket", "user.password_reset_ticket.consume", "user", ticket.getUser().getId().toString(), ticket.getUser().getUsername());
    }

    /**
     * 查询密码重置票据审计清单，不暴露明文 token 或 token hash。
     */
    @Transactional(readOnly = true)
    public List<PasswordResetTicketDetailResponse> listPasswordResetTickets(
        UUID userId,
        Boolean consumed,
        Boolean usable,
        String keyword,
        Integer limit
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Instant now = Instant.now();
        int cappedLimit = limit == null ? 100 : Math.clamp(limit, 1, 500);
        return passwordResetTickets.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(ticket -> userId == null || ticket.getUser().getId().equals(userId))
            .filter(ticket -> consumed == null || (ticket.getConsumedAt() != null) == consumed)
            .filter(ticket -> usable == null || ticket.isUsable(now) == usable)
            .filter(ticket -> normalizedKeyword == null || matchesPasswordResetTicketKeyword(ticket, normalizedKeyword))
            .limit(cappedLimit)
            .map(ticket -> toDetailResponse(ticket, now))
            .toList();
    }

    /**
     * 查询密码重置票据元数据详情。
     */
    @Transactional(readOnly = true)
    public PasswordResetTicketDetailResponse getPasswordResetTicket(UUID ticketId) {
        Instant now = Instant.now();
        return passwordResetTickets.findById(ticketId)
            .map(ticket -> toDetailResponse(ticket, now))
            .orElseThrow(() -> new NotFoundException("Password reset ticket not found: " + ticketId));
    }

    /**
     * 作废密码重置票据，使其无法继续被消费。
     */
    @Transactional
    public PasswordResetTicketDetailResponse revokePasswordResetTicket(UUID ticketId, String actor) {
        PasswordResetTicket ticket = passwordResetTickets.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("Password reset ticket not found: " + ticketId));
        ticket.revoke();
        auditService.record(actor, "user.password_reset_ticket.revoke", "user", ticket.getUser().getId().toString(), ticket.getUser().getUsername());
        return toDetailResponse(ticket, Instant.now());
    }

    /**
     * 校验用户密码，并根据失败次数执行锁定策略。
     */
    @Transactional
    public VerifyPasswordResponse verifyPassword(String username, String rawPassword, String actor) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        UserCredential credential = credentials.findByUserAndType(user, CredentialType.PASSWORD)
            .orElseThrow(() -> new NotFoundException("Password credential not found for user: " + username));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                null,
                AuthenticationEventType.LOGIN_FAILURE,
                null,
                null,
                "account_status=" + user.getStatus()));
            auditService.record(actor, "user.password.verify", "user", user.getId().toString(), "valid=false;status=" + user.getStatus());
            return toPasswordResponse(false, credential, user);
        }
        boolean valid = passwordEncoder.matches(rawPassword, credential.getSecretHash());
        if (valid) {
            credential.markUsed();
            boolean expired = credential.isExpired(Instant.now());
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                null,
                AuthenticationEventType.LOGIN_SUCCESS,
                null,
                null,
                expired ? "password_expired" : "password_verified"));
        } else {
            boolean locked = recordPasswordFailure(user, credential);
            if (locked) {
                endActiveSessions(user, actor, "user.password.lock");
            }
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                null,
                AuthenticationEventType.LOGIN_FAILURE,
                null,
                null,
                "failed_attempts=" + credential.getFailedAttempts() + ";locked=" + locked));
            if (locked) {
                auditService.record(actor, "user.password.lock", "user", user.getId().toString(), "failed_attempts=" + credential.getFailedAttempts());
            }
        }
        auditService.record(actor, "user.password.verify", "user", user.getId().toString(), "valid=" + valid);
        return toPasswordResponse(valid, credential, user);
    }

    /**
     * 注册 MFA 因子；TOTP 因子可自动生成密钥。
     */
    @Transactional
    public MfaFactorResponse registerMfaFactor(UUID userId, RegisterMfaFactorRequest request, String actor) {
        UserAccount user = getEntity(userId);
        if (request.type() == MfaFactorType.RECOVERY_CODE) {
            throw new IllegalArgumentException("Use the recovery code generation endpoint for recovery codes");
        }
        if (request.type() == MfaFactorType.SMS && (user.getMobile() == null || user.getMobile().isBlank())) {
            throw new IllegalArgumentException("短信 MFA 需要先绑定手机号");
        }
        if (request.type() == MfaFactorType.EMAIL && (user.getEmail() == null || user.getEmail().isBlank())) {
            throw new IllegalArgumentException("邮件 MFA 需要先设置邮箱");
        }
        boolean hasSecret = request.secret() != null && !request.secret().isBlank();
        String secret = request.type() == MfaFactorType.TOTP
            ? (hasSecret ? mfaVerification.normalizeTotpSecret(request.secret()) : mfaVerification.generateTotpSecret())
            : null;
        MfaFactor saved = mfaFactors.save(new MfaFactor(user, request.type(), request.name(), secret));
        auditService.record(actor, "user.mfa.register", "user", userId.toString(), request.type().name());
        return toResponse(saved);
    }

    /**
     * 生成新的 MFA 恢复码并替换旧恢复码。
     */
    @Transactional
    public RecoveryCodesResponse generateRecoveryCodes(UUID userId, String actor) {
        UserAccount user = getEntity(userId);
        List<String> codes = java.util.stream.IntStream.range(0, 10)
            .mapToObj(index -> mfaVerification.generateRecoveryCode())
            .toList();
        String secret = codes.stream().map(tokens::sha256).collect(java.util.stream.Collectors.joining("\n"));
        MfaFactor factor = mfaFactors.findByUserIdAndType(userId, MfaFactorType.RECOVERY_CODE)
            .map(existing -> {
                existing.replaceSecret(secret);
                existing.verify();
                return existing;
            })
            .orElseGet(() -> {
                MfaFactor saved = new MfaFactor(user, MfaFactorType.RECOVERY_CODE, "Recovery codes", secret);
                saved.verify();
                return mfaFactors.save(saved);
            });
        auditService.record(actor, "user.mfa.recovery_codes.generate", "user", userId.toString(), user.getUsername());
        return new RecoveryCodesResponse(factor.getId(), codes);
    }

    /**
     * 查询用户 MFA 因子列表。
     */
    @Transactional(readOnly = true)
    public List<MfaFactorResponse> listMfaFactors(UUID userId) {
        getEntity(userId);
        return mfaFactors.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    /**
     * 查询用户指定 MFA 因子详情。
     */
    @Transactional(readOnly = true)
    public MfaFactorResponse getMfaFactor(UUID userId, UUID factorId) {
        return toResponse(getUserMfaFactor(userId, factorId));
    }

    /**
     * 更新 MFA 因子的展示名称。
     */
    @Transactional
    public MfaFactorResponse updateMfaFactor(UUID userId, UUID factorId, UpdateMfaFactorRequest request, String actor) {
        MfaFactor factor = getUserMfaFactor(userId, factorId);
        factor.rename(request.name());
        auditService.record(actor, "user.mfa.update", "user", userId.toString(), factor.getType().name());
        return toResponse(factor);
    }

    /**
     * 启用指定 MFA 因子。
     */
    @Transactional
    public MfaFactorResponse enableMfaFactor(UUID userId, UUID factorId, String actor) {
        MfaFactor factor = getUserMfaFactor(userId, factorId);
        factor.enable();
        auditService.record(actor, "user.mfa.enable", "user", userId.toString(), factor.getType().name());
        return toResponse(factor);
    }

    /**
     * 停用指定 MFA 因子，保留历史挑战记录。
     */
    @Transactional
    public MfaFactorResponse disableMfaFactor(UUID userId, UUID factorId, String actor) {
        MfaFactor factor = getUserMfaFactor(userId, factorId);
        factor.disable();
        auditService.record(actor, "user.mfa.disable", "user", userId.toString(), factor.getType().name());
        return toResponse(factor);
    }

    /**
     * 删除未产生挑战历史的 MFA 因子，避免破坏审计链路。
     */
    @Transactional
    public void deleteMfaFactor(UUID userId, UUID factorId, String actor) {
        MfaFactor factor = getUserMfaFactor(userId, factorId);
        if (mfaChallenges.existsByFactorId(factorId)) {
            throw new IllegalArgumentException("MFA factor has challenge history and can only be disabled");
        }
        mfaFactors.delete(factor);
        auditService.record(actor, "user.mfa.delete", "user", userId.toString(), factor.getType().name());
    }

    /**
     * 发起 MFA 挑战，短信/邮件/WebAuthn 原型因子会生成一次性验证码。
     */
    @Transactional
    public MfaChallengeResponse startMfaChallenge(UUID userId, StartMfaChallengeRequest request, String actor) {
        UserAccount user = getEntity(userId);
        MfaFactor factor = mfaFactors.findById(request.factorId())
            .orElseThrow(() -> new NotFoundException("MFA factor not found: " + request.factorId()));
        if (!factor.getUser().getId().equals(userId) || !factor.isEnabled()) {
            throw new IllegalArgumentException("MFA factor is not available for this user");
        }
        MfaVerificationService.IssuedCode issued = mfaVerification.issueCode(user, factor);
        MfaChallenge saved = mfaChallenges.save(new MfaChallenge(
            user,
            factor,
            tokens.generateToken(24),
            issued.codeHash(),
            Instant.now().plus(MFA_CHALLENGE_TTL)));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            null,
            AuthenticationEventType.MFA_CHALLENGE,
            null,
            null,
            "factor=" + factor.getType()));
        auditService.record(actor, "user.mfa.challenge.start", "user", userId.toString(), factor.getType().name());
        return new MfaChallengeResponse(
            saved.getChallengeId(),
            factor.getId(),
            factor.getType(),
            saved.getStatus(),
            mfaVerification.deliveryHint(factor),
            issued.echoCode());
    }

    /**
     * 查询 MFA 挑战审计记录，用于安全排查。
     */
    @Transactional(readOnly = true)
    public List<MfaChallengeDetailResponse> listMfaChallenges(
        UUID userId,
        UUID factorId,
        MfaChallengeStatus status,
        MfaFactorType type,
        String keyword,
        Integer limit
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int cappedLimit = limit == null ? 100 : Math.clamp(limit, 1, 500);
        return mfaChallenges.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(challenge -> userId == null || challenge.getUser().getId().equals(userId))
            .filter(challenge -> factorId == null || challenge.getFactor().getId().equals(factorId))
            .filter(challenge -> status == null || challenge.getStatus() == status)
            .filter(challenge -> type == null || challenge.getFactor().getType() == type)
            .filter(challenge -> normalizedKeyword == null || matchesChallengeKeyword(challenge, normalizedKeyword))
            .limit(cappedLimit)
            .map(this::toChallengeDetailResponse)
            .toList();
    }

    /**
     * 查询单个 MFA 挑战记录详情。
     */
    @Transactional(readOnly = true)
    public MfaChallengeDetailResponse getMfaChallenge(UUID challengeRecordId) {
        return mfaChallenges.findById(challengeRecordId)
            .map(this::toChallengeDetailResponse)
            .orElseThrow(() -> new NotFoundException("MFA challenge not found: " + challengeRecordId));
    }

    /**
     * 校验 MFA 挑战，并写入成功或失败认证事件。
     */
    @Transactional
    public VerifyMfaChallengeResponse verifyMfaChallenge(VerifyMfaChallengeRequest request, String actor) {
        MfaChallenge challenge = mfaChallenges.findByChallengeId(request.challengeId())
            .filter(found -> !MfaChallenge.PURPOSE_LOGIN.equals(found.getPurpose()))
            .orElseThrow(() -> new NotFoundException("MFA challenge not found: " + request.challengeId()));
        return verifyChallenge(challenge, request.code(), actor);
    }

    /**
     * 用户自助校验自己发起的 MFA 挑战，用于绑定新因子后的首次验证。
     */
    @Transactional
    public VerifyMfaChallengeResponse verifyOwnMfaChallenge(UUID userId, String challengeId, String code, String actor) {
        MfaChallenge challenge = mfaChallenges.findByChallengeId(challengeId)
            .filter(found -> found.getUser().getId().equals(userId))
            .filter(found -> !MfaChallenge.PURPOSE_LOGIN.equals(found.getPurpose()))
            .orElseThrow(() -> new NotFoundException("MFA challenge not found: " + challengeId));
        return verifyChallenge(challenge, code, actor);
    }

    private VerifyMfaChallengeResponse verifyChallenge(MfaChallenge challenge, String code, String actor) {
        if (!challenge.isUsable(Instant.now())) {
            challenge.expire();
            authenticationEvents.save(new AuthenticationEvent(
                null,
                challenge.getUser(),
                null,
                AuthenticationEventType.MFA_FAILURE,
                null,
                null,
                "challenge_expired"));
            return new VerifyMfaChallengeResponse(false, challenge.getStatus());
        }
        boolean valid = mfaVerification.verify(challenge, code);
        if (valid) {
            challenge.verify();
            challenge.getFactor().verify();
            if (MfaVerificationService.isLoginCapable(challenge.getFactor())) {
                loginSessions.clearRestriction(challenge.getUser(), SessionRestriction.MFA_ENROLLMENT);
            }
            authenticationEvents.save(new AuthenticationEvent(
                null,
                challenge.getUser(),
                null,
                AuthenticationEventType.MFA_SUCCESS,
                null,
                null,
                "factor=" + challenge.getFactor().getType()));
        } else {
            challenge.fail();
            authenticationEvents.save(new AuthenticationEvent(
                null,
                challenge.getUser(),
                null,
                AuthenticationEventType.MFA_FAILURE,
                null,
                null,
                "invalid_code"));
        }
        auditService.record(actor, "user.mfa.challenge.verify", "user", challenge.getUser().getId().toString(), "valid=" + valid);
        return new VerifyMfaChallengeResponse(valid, challenge.getStatus());
    }

    /**
     * 将用户加入用户组，获得该组继承角色和权限。
     */
    @Transactional
    public UserResponse joinGroup(UUID userId, UUID groupId, String actor) {
        UserAccount user = getEntity(userId);
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        user.join(group);
        auditService.record(actor, "user.join_group", "user", userId.toString(), group.getCode());
        return toResponse(user);
    }

    /**
     * 将用户移出用户组。
     */
    @Transactional
    public UserResponse leaveGroup(UUID userId, UUID groupId, String actor) {
        UserAccount user = getEntity(userId);
        UserGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("User group not found: " + groupId));
        user.leave(group);
        auditService.record(actor, "user.leave_group", "user", userId.toString(), group.getCode());
        return toResponse(user);
    }

    /**
     * 给用户授予直接角色。
     */
    @Transactional
    public UserResponse grantRole(UUID userId, UUID roleId, String actor) {
        UserAccount user = getEntity(userId);
        Role role = roles.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        user.grant(role);
        auditService.record(actor, "user.grant_role", "user", userId.toString(), role.getCode());
        return toResponse(user);
    }

    /**
     * 撤销用户直接角色。
     */
    @Transactional
    public UserResponse revokeRole(UUID userId, UUID roleId, String actor) {
        UserAccount user = getEntity(userId);
        Role role = roles.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Role not found: " + roleId));
        user.revoke(role);
        auditService.record(actor, "user.revoke_role", "user", userId.toString(), role.getCode());
        return toResponse(user);
    }

    /**
     * 查询全部用户。
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return list(null, null, null, null);
    }

    /**
     * 按租户查询用户。
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list(UUID tenantId) {
        return list(tenantId, null, null, null);
    }

    /**
     * 按租户、组织、状态和关键字查询用户目录。
     */
    @Transactional(readOnly = true)
    public List<UserResponse> list(UUID tenantId, UUID organizationId, AccountStatus status, String keyword) {
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase();
        List<UserAccount> values = tenantId == null ? users.findAll() : users.findByTenantId(tenantId);
        return values.stream()
            .filter(user -> organizationId == null || (user.getOrganization() != null && user.getOrganization().getId().equals(organizationId)))
            .filter(user -> status == null || user.getStatus() == status)
            .filter(user -> matchesKeyword(user, normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    /**
     * 获取用户实体，供内部业务逻辑复用。
     */
    @Transactional(readOnly = true)
    public UserAccount getEntity(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    /**
     * 按用户 ID 查询用户详情。
     */
    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return toResponse(getEntity(userId));
    }

    /**
     * 根据当前认证用户名查询用户详情。
     */
    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return toResponse(user);
    }

    /**
     * 计算用户最终生效的角色和权限来源。
     */
    @Transactional(readOnly = true)
    public UserEffectiveAccessResponse effectiveAccess(UUID userId) {
        UserAccount user = getEntity(userId);
        Map<String, PermissionAccumulator> permissions = new java.util.TreeMap<>();
        user.getRoles().forEach(role -> collectRolePermissions(role, "direct", permissions));
        user.getGroups().forEach(group -> group.getRoles()
            .forEach(role -> collectRolePermissions(role, "group:" + group.getCode(), permissions)));
        Set<String> directRoles = user.getRoles().stream()
            .map(Role::getCode)
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> groupRoles = user.getGroups().stream()
            .flatMap(group -> group.getRoles().stream().map(role -> group.getCode() + ":" + role.getCode()))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        return new UserEffectiveAccessResponse(
            user.getId(),
            user.getUsername(),
            user.getStatus(),
            directRoles,
            groupRoles,
            permissions.values().stream().map(PermissionAccumulator::toResponse).toList());
    }

    private UserResponse toResponse(UserAccount user) {
        UUID organizationId = user.getOrganization() == null ? null : user.getOrganization().getId();
        String identitySourceType = user.getIdentitySource() == null ? null : user.getIdentitySource().getType().name();
        String identitySourceName = user.getIdentitySource() == null ? null : user.getIdentitySource().getName();
        UUID tenantId = user.getTenant() == null ? null : user.getTenant().getId();
        Set<String> groupCodes = user.getGroups().stream().map(UserGroup::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> roleCodes = user.getRoles().stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getMobile(),
            user.getStatus(),
            tenantId,
            organizationId,
            identitySourceType,
            identitySourceName,
            groupCodes,
            roleCodes,
            user.getAvatarUrl(),
            roleNames);
    }

    private boolean matchesKeyword(UserAccount user, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return contains(user.getUsername(), keyword)
            || contains(user.getDisplayName(), keyword)
            || contains(user.getEmail(), keyword)
            || contains(user.getMobile(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void validatePasswordPolicy(UserAccount user, String password) {
        int minLength = authenticationPolicyService.currentPasswordMinLength();
        if (password.length() < minLength) {
            throw new IllegalArgumentException("密码长度不能少于 " + minLength + " 位");
        }
        int maxLength = authenticationPolicyService.currentPasswordMaxLength();
        if (password.length() > maxLength) {
            throw new IllegalArgumentException("密码长度不能超过 " + maxLength + " 位");
        }
        validatePasswordComplexity(password, authenticationPolicyService.currentPasswordComplexity());
        validateRepeatedChars(password, authenticationPolicyService.currentPasswordMaxRepeatedChars());
        validateUserInfoPassword(user, password);
        validateWeakPassword(password);
        validatePasswordExtensionRules(password);
        validateIllegalSequence(password);
    }

    private void collectRolePermissions(Role role, String source, Map<String, PermissionAccumulator> permissions) {
        role.getPermissions().forEach(permission -> permissions
            .computeIfAbsent(permission.getCode(), code -> new PermissionAccumulator(permission))
            .add(role.getCode(), source));
    }

    private static final class PermissionAccumulator {
        private final Permission permission;
        private final Set<String> roles = new java.util.TreeSet<>();
        private final Set<String> sources = new java.util.TreeSet<>();

        private PermissionAccumulator(Permission permission) {
            this.permission = permission;
        }

        private void add(String roleCode, String source) {
            roles.add(roleCode);
            sources.add(source);
        }

        private EffectivePermissionResponse toResponse() {
            return new EffectivePermissionResponse(
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                roles,
                sources);
        }
    }

    private void disableDirectApplicationAssignments(UserAccount user, String actor, String reason) {
        List<ApplicationAssignment> directAssignments = applicationAssignments.findByUserId(user.getId()).stream()
            .filter(ApplicationAssignment::isEnabled)
            .toList();
        directAssignments.forEach(ApplicationAssignment::disable);
        if (!directAssignments.isEmpty()) {
            auditService.record(
                actor,
                "user.application_assignments.disable",
                "user",
                user.getId().toString(),
                reason + ";count=" + directAssignments.size());
        }
    }

    private void endActiveSessions(UserAccount user, String actor, String reason) {
        List<AuthenticationSession> activeSessions = authenticationSessions.findByUserIdAndActive(user.getId(), true);
        activeSessions.forEach(session -> {
            session.end();
            authenticationEvents.save(new AuthenticationEvent(
                session,
                user,
                session.getApplication(),
                AuthenticationEventType.LOGOUT,
                session.getIpAddress(),
                session.getUserAgent(),
                reason + ";ended_by=" + actor));
        });
        if (!activeSessions.isEmpty()) {
            auditService.record(
                actor,
                "user.sessions.end",
                "user",
                user.getId().toString(),
                reason + ";count=" + activeSessions.size());
        }
    }

    private void revokeOAuthTokens(UserAccount user, String actor, String reason) {
        Instant now = Instant.now();
        List<OAuthAccessToken> activeAccessTokens = oauthAccessTokens.findByUserId(user.getId()).stream()
            .filter(token -> token.isActive(now))
            .toList();
        List<OAuthRefreshToken> activeRefreshTokens = oauthRefreshTokens.findByUserId(user.getId()).stream()
            .filter(token -> token.isActive(now))
            .toList();
        activeAccessTokens.forEach(token -> {
            token.revoke();
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                token.getApplication(),
                AuthenticationEventType.TOKEN_REVOKED,
                null,
                null,
                reason + ";token_type=access;ended_by=" + actor));
        });
        activeRefreshTokens.forEach(token -> {
            token.revoke();
            authenticationEvents.save(new AuthenticationEvent(
                null,
                user,
                token.getApplication(),
                AuthenticationEventType.TOKEN_REVOKED,
                null,
                null,
                reason + ";token_type=refresh;ended_by=" + actor));
        });
        if (!activeAccessTokens.isEmpty() || !activeRefreshTokens.isEmpty()) {
            auditService.record(
                actor,
                "user.oauth_tokens.revoke",
                "user",
                user.getId().toString(),
                reason + ";access=" + activeAccessTokens.size() + ";refresh=" + activeRefreshTokens.size());
        }
    }

    private void rotatePassword(UserAccount user, String rawPassword, boolean temporary) {
        validatePasswordPolicy(user, rawPassword);
        UserCredential credential = credentials.findByUserAndType(user, CredentialType.PASSWORD)
            .orElseGet(() -> new UserCredential(user, CredentialType.PASSWORD, "", temporary));
        validatePasswordHistory(user, credential, rawPassword);
        archiveCurrentPassword(user, credential);
        credential.rotate(passwordEncoder.encode(rawPassword), temporary, passwordExpiresAt());
        credentials.save(credential);
    }

    private void validatePasswordHistory(UserAccount user, UserCredential credential, String rawPassword) {
        int historyCount = authenticationPolicyService.currentPasswordHistoryCount();
        if (historyCount <= 0) {
            return;
        }
        if (!credential.getSecretHash().isBlank() && passwordEncoder.matches(rawPassword, credential.getSecretHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        boolean reused = credentialHistories.findByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), CredentialType.PASSWORD).stream()
            .limit(historyCount)
            .anyMatch(history -> passwordEncoder.matches(rawPassword, history.getSecretHash()));
        if (reused) {
            throw new IllegalArgumentException("新密码不能与最近 " + historyCount + " 次使用过的密码相同");
        }
    }

    private void archiveCurrentPassword(UserAccount user, UserCredential credential) {
        int historyCount = authenticationPolicyService.currentPasswordHistoryCount();
        if (historyCount <= 0 || credential.getSecretHash().isBlank()) {
            return;
        }
        credentialHistories.save(new UserCredentialHistory(user, CredentialType.PASSWORD, credential.getSecretHash()));
        List<UserCredentialHistory> histories = credentialHistories.findByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), CredentialType.PASSWORD);
        histories.stream()
            .skip(historyCount)
            .forEach(credentialHistories::delete);
    }

    private Instant passwordExpiresAt() {
        int expiresInDays = authenticationPolicyService.currentPasswordExpiresInDays();
        return expiresInDays <= 0 ? null : Instant.now().plus(Duration.ofDays(expiresInDays));
    }

    private VerifyPasswordResponse toPasswordResponse(boolean valid, UserCredential credential, UserAccount user) {
        boolean expired = credential.isExpired(Instant.now());
        return new VerifyPasswordResponse(
            valid,
            credential.isTemporary(),
            credential.getFailedAttempts(),
            user.getStatus() == AccountStatus.LOCKED,
            expired,
            valid && (credential.isTemporary() || expired));
    }

    private int normalizeResetTicketMinutes(Integer expiresInMinutes) {
        if (expiresInMinutes == null) {
            return DEFAULT_PASSWORD_RESET_TICKET_MINUTES;
        }
        if (expiresInMinutes <= 0 || expiresInMinutes > MAX_PASSWORD_RESET_TICKET_MINUTES) {
            throw new IllegalArgumentException("重置票据有效期必须在 1 到 " + MAX_PASSWORD_RESET_TICKET_MINUTES + " 分钟之间");
        }
        return expiresInMinutes;
    }

    /**
     * 暂停或离职的账号不允许通过重置票据恢复密码。
     */
    private void requireResettable(UserAccount user) {
        if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.DEPARTED) {
            throw new IllegalArgumentException("账号状态为 " + user.getStatus() + "，不能重置密码");
        }
    }

    /**
     * 记录一次密码失败，按全局安全设置的失败窗口计数，达到上限时锁定账号。
     */
    private boolean recordPasswordFailure(UserAccount user, UserCredential credential) {
        int window = Math.max(1, securitySettings.general().loginFailureWindowMinutes());
        credential.markFailed(Duration.ofMinutes(window));
        int maxAttempts = authenticationPolicyService.currentPasswordMaxFailureAttempts();
        boolean locked = user.getStatus() == AccountStatus.ACTIVE && credential.getFailedAttempts() >= maxAttempts;
        if (locked) {
            user.lock();
            credential.markLocked();
        }
        return locked;
    }

    private String requireUniqueUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        String normalized = username.trim();
        if (users.existsByUsername(normalized)) {
            throw new ConflictException("用户名已存在: " + normalized);
        }
        return normalized;
    }

    /**
     * 规范化手机号并校验唯一，空值表示不设置手机号。
     */
    private String uniqueMobile(String mobile, UUID userId) {
        if (mobile == null || mobile.isBlank()) {
            return null;
        }
        String normalized = SmsVerificationService.normalizeMobile(mobile);
        boolean taken = userId == null ? users.existsByMobile(normalized) : users.existsByMobileAndIdNot(normalized, userId);
        if (taken) {
            throw new ConflictException("该手机号已被其他用户使用");
        }
        return normalized;
    }

    /**
     * 当前请求所属的登录会话 ID，由会话令牌过滤器写入认证详情。
     */
    private UUID currentSessionId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getDetails() instanceof UUID sessionId ? sessionId : null;
    }

    private void validatePasswordComplexity(String password, String complexity) {
        int categories = 0;
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (hasDigit) {
            categories++;
        }
        if (hasLower) {
            categories++;
        }
        if (hasUpper) {
            categories++;
        }
        if (hasSpecial) {
            categories++;
        }
        switch (complexity == null ? "three" : complexity) {
            case "any" -> {
            }
            case "number-letter" -> require(hasDigit && hasLetter, "密码必须同时包含数字和字母");
            case "number-upper" -> require(hasDigit && hasUpper, "密码必须同时包含数字和大写字母");
            case "all" -> require(hasDigit && hasUpper && hasLower && hasSpecial, "密码必须同时包含数字、大写字母、小写字母和特殊字符");
            case "two" -> require(categories >= 2, "密码至少需要包含数字、大写字母、小写字母、特殊字符中的两类");
            default -> require(categories >= 3, "密码至少需要包含数字、大写字母、小写字母、特殊字符中的三类");
        }
    }

    private void validateRepeatedChars(String password, int maxRepeatedChars) {
        if (maxRepeatedChars <= 0) {
            return;
        }
        int runLength = 0;
        int previous = -1;
        for (int index = 0; index < password.length(); index++) {
            int current = password.charAt(index);
            runLength = current == previous ? runLength + 1 : 1;
            previous = current;
            if (runLength > maxRepeatedChars) {
                throw new IllegalArgumentException("密码中连续重复的字符过多");
            }
        }
    }

    private void validateUserInfoPassword(UserAccount user, String password) {
        if (!authenticationPolicyService.currentPasswordCheckUserInfo()) {
            return;
        }
        String normalizedPassword = password.toLowerCase();
        if (containsSensitiveUserValue(normalizedPassword, user.getUsername())
            || containsSensitiveUserValue(normalizedPassword, user.getMobile())
            || containsSensitiveUserValue(normalizedPassword, user.getDisplayName())
            || containsSensitiveUserValue(normalizedPassword, emailPrefix(user.getEmail()))) {
            throw new IllegalArgumentException("密码不能包含用户名、手机号、邮箱等个人信息");
        }
    }

    private void validateWeakPassword(String password) {
        if (!authenticationPolicyService.currentPasswordWeakPasswordCheckEnabled()) {
            return;
        }
        String normalizedPassword = password.toLowerCase();
        java.util.Set<String> weakPasswords = new java.util.HashSet<>(java.util.Set.of(
            "123456",
            "12345678",
            "123456789",
            "password",
            "qwerty",
            "admin",
            "admin123",
            "letmein",
            "welcome"));
        weakPasswords.addAll(authenticationPolicyService.currentAdditionalWeakPasswords());
        if (weakPasswords.contains(normalizedPassword)) {
            throw new IllegalArgumentException("密码过于简单，属于常见弱密码");
        }
    }

    private void validatePasswordExtensionRules(String password) {
        java.util.Set<String> rules = authenticationPolicyService.currentPasswordExtensionRules();
        if (rules.contains("serial-number") && containsAscendingRun(password, '0', '9')) {
            throw new IllegalArgumentException("密码不能包含连续的数字");
        }
        if (rules.contains("serial-letter") && (containsAscendingRun(password.toLowerCase(), 'a', 'z'))) {
            throw new IllegalArgumentException("密码不能包含连续的字母");
        }
    }

    private static final int ILLEGAL_SEQUENCE_LENGTH = 4;
    private static final List<String> ILLEGAL_SEQUENCE_SOURCES = List.of(
        "abcdefghijklmnopqrstuvwxyz",
        "01234567890",
        "`1234567890-=",
        "qwertyuiop[]\\",
        "asdfghjkl;'",
        "zxcvbnm,./",
        "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/",
        "!@#$%^&*()_+");

    // 开启非法字符序列检查时，禁止出现 4 位及以上的键盘相邻、连续字母或连续数字序列（正序或倒序）。
    private void validateIllegalSequence(String password) {
        if (!authenticationPolicyService.currentPasswordIllegalSequenceCheckEnabled()) {
            return;
        }
        String normalized = password.toLowerCase();
        for (int index = 0; index + ILLEGAL_SEQUENCE_LENGTH <= normalized.length(); index++) {
            String window = normalized.substring(index, index + ILLEGAL_SEQUENCE_LENGTH);
            String reversed = new StringBuilder(window).reverse().toString();
            for (String source : ILLEGAL_SEQUENCE_SOURCES) {
                if (source.contains(window) || source.contains(reversed)) {
                    throw new IllegalArgumentException("密码不能包含键盘或字母顺序序列，例如 \"" + window + "\"");
                }
            }
        }
    }

    private boolean containsSensitiveUserValue(String password, String value) {
        return value != null && value.length() >= 3 && password.contains(value.toLowerCase());
    }

    private String emailPrefix(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? email : email.substring(0, at);
    }

    private boolean containsAscendingRun(String value, char first, char last) {
        for (int index = 0; index <= value.length() - 3; index++) {
            char a = value.charAt(index);
            char b = value.charAt(index + 1);
            char c = value.charAt(index + 2);
            if (a >= first && c <= last && b == a + 1 && c == b + 1) {
                return true;
            }
        }
        return false;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private PasswordResetTicketResponse toResponse(PasswordResetTicket ticket, String resetToken) {
        return new PasswordResetTicketResponse(
            ticket.getId(),
            ticket.getUser().getId(),
            resetToken,
            ticket.getExpiresAt(),
            ticket.getConsumedAt() != null);
    }

    private PasswordResetTicketDetailResponse toDetailResponse(PasswordResetTicket ticket, Instant now) {
        return new PasswordResetTicketDetailResponse(
            ticket.getId(),
            ticket.getUser().getId(),
            ticket.getUser().getUsername(),
            ticket.getCreatedAt(),
            ticket.getExpiresAt(),
            ticket.getConsumedAt(),
            ticket.getRequestedBy(),
            !ticket.getExpiresAt().isAfter(now),
            ticket.isUsable(now));
    }

    private boolean matchesPasswordResetTicketKeyword(PasswordResetTicket ticket, String keyword) {
        return contains(ticket.getUser().getUsername(), keyword)
            || contains(ticket.getUser().getDisplayName(), keyword)
            || contains(ticket.getRequestedBy(), keyword);
    }

    private MfaFactorResponse toResponse(MfaFactor factor) {
        return new MfaFactorResponse(
            factor.getId(),
            factor.getType(),
            factor.getName(),
            factor.isVerified(),
            factor.isEnabled(),
            factor.getType() == MfaFactorType.TOTP && !factor.isVerified() ? factor.getSecret() : null,
            factor.getType() == MfaFactorType.TOTP && !factor.isVerified() ? mfaVerification.provisioningUri(factor) : null);
    }

    private MfaChallengeDetailResponse toChallengeDetailResponse(MfaChallenge challenge) {
        return new MfaChallengeDetailResponse(
            challenge.getId(),
            challenge.getChallengeId(),
            challenge.getUser().getId(),
            challenge.getUser().getUsername(),
            challenge.getFactor().getId(),
            challenge.getFactor().getType(),
            challenge.getStatus(),
            challenge.getCreatedAt(),
            challenge.getExpiresAt(),
            challenge.getVerifiedAt(),
            challenge.getAttempts());
    }

    private MfaFactor getUserMfaFactor(UUID userId, UUID factorId) {
        getEntity(userId);
        MfaFactor factor = mfaFactors.findById(factorId)
            .orElseThrow(() -> new NotFoundException("MFA factor not found: " + factorId));
        if (!factor.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("MFA factor does not belong to this user");
        }
        return factor;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesChallengeKeyword(MfaChallenge challenge, String keyword) {
        return contains(challenge.getChallengeId(), keyword)
            || contains(challenge.getUser().getUsername(), keyword)
            || contains(challenge.getUser().getDisplayName(), keyword)
            || contains(challenge.getFactor().getName(), keyword)
            || challenge.getFactor().getType().name().toLowerCase().contains(keyword)
            || challenge.getStatus().name().toLowerCase().contains(keyword);
    }
}
