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

import com.antiam.common.NotFoundException;
import com.antiam.common.TokenSupport;
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
import com.antiam.domain.Permission;
import com.antiam.domain.Role;
import com.antiam.domain.Tenant;
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
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int TOTP_STEP_SECONDS = 30;
    private static final int TOTP_WINDOW = 1;
    private static final int DEFAULT_PASSWORD_RESET_TICKET_MINUTES = 30;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

    /**
     * 创建内部用户账号，并在提供初始密码时创建临时密码凭据。
     */
    @Transactional
    public UserResponse create(CreateUserRequest request, String actor) {
        Organization organization = request.organizationId() == null ? null : organizationService.getEntity(request.organizationId());
        Tenant tenant = request.tenantId() == null ? null : tenantService.getEntity(request.tenantId());
        UserAccount saved = users.save(new UserAccount(
            request.username(),
            request.displayName(),
            request.email(),
            request.mobile(),
            tenant,
            organization));
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
        UserAccount saved = users.save(new UserAccount(username, displayName, email, mobile, null, null));
        auditService.record(actor, "scim.user.create", "user", saved.getId().toString(), saved.getUsername());
        return toResponse(saved);
    }

    /**
     * 更新用户基础资料和所属组织。
     */
    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request, String actor) {
        UserAccount user = getEntity(userId);
        Organization organization = request.organizationId() == null ? null : organizationService.getEntity(request.organizationId());
        user.updateProfile(request.displayName(), request.email(), request.mobile(), organization);
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
     * 管理员设置用户密码，并应用当前密码策略。
     */
    @Transactional
    public void setPassword(UUID userId, SetPasswordRequest request, String actor) {
        UserAccount user = getEntity(userId);
        rotatePassword(user, request.password(), request.temporary());
        auditService.record(actor, "user.password.set", "user", userId.toString(), user.getUsername());
    }

    /**
     * 当前用户自助修改密码，会校验当前密码并记录认证事件。
     */
    @Transactional
    public void changeOwnPassword(String username, ChangeOwnPasswordRequest request) {
        UserAccount user = users.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        UserCredential credential = credentials.findByUserAndType(user, CredentialType.PASSWORD)
            .orElseThrow(() -> new NotFoundException("Password credential not found for user: " + username));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("User account is not active");
        }
        if (!passwordEncoder.matches(request.currentPassword(), credential.getSecretHash())) {
            credential.markFailed();
            int maxAttempts = authenticationPolicyService.currentPasswordMaxFailureAttempts();
            boolean locked = credential.getFailedAttempts() >= maxAttempts;
            if (locked) {
                user.lock();
                credential.markLocked();
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
            throw new IllegalArgumentException("Current password is invalid");
        }
        rotatePassword(user, request.newPassword(), false);
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            null,
            AuthenticationEventType.LOGIN_SUCCESS,
            null,
            null,
            "password_changed"));
        auditService.record(username, "user.password.change", "user", user.getId().toString(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public void sendMobileBindingCode(UUID userId, String mobile) {
        getEntity(userId);
        smsVerificationService.sendVerificationCode(mobile, "BIND_MOBILE");
    }

    @Transactional
    public UserResponse bindMobile(UUID userId, BindMobileRequest request, String actor) {
        UserAccount user = getEntity(userId);
        smsVerificationService.verify(request.mobile(), "BIND_MOBILE", request.code());
        users.findByMobile(request.mobile())
            .filter(existing -> !existing.getId().equals(userId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Mobile is already bound to another user");
            });
        user.updateProfile(user.getDisplayName(), user.getEmail(), request.mobile(), user.getOrganization());
        auditService.record(actor, "user.mobile.bind", "user", userId.toString(), request.mobile());
        return toResponse(user);
    }

    /**
     * 创建一次性密码重置票据，仅在创建响应中返回明文 resetToken。
     */
    @Transactional
    public PasswordResetTicketResponse createPasswordResetTicket(CreatePasswordResetTicketRequest request, String actor) {
        UserAccount user = getEntity(request.userId());
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
        rotatePassword(ticket.getUser(), request.newPassword(), request.temporary());
        ticket.consume();
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
            credential.markFailed();
            int maxAttempts = authenticationPolicyService.currentPasswordMaxFailureAttempts();
            boolean locked = credential.getFailedAttempts() >= maxAttempts;
            if (locked) {
                user.lock();
                credential.markLocked();
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
        String secret = request.type() == MfaFactorType.TOTP && (request.secret() == null || request.secret().isBlank())
            ? generateTotpSecret()
            : request.secret();
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
            .mapToObj(index -> generateRecoveryCode())
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
        String code = factor.getType() == MfaFactorType.TOTP || factor.getType() == MfaFactorType.RECOVERY_CODE ? null : generateMfaCode();
        String codeHash = code == null ? factor.getType().name().toLowerCase() : tokens.sha256(code);
        MfaChallenge saved = mfaChallenges.save(new MfaChallenge(
            user,
            factor,
            tokens.generateToken(24),
            codeHash,
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
            deliveryHint(factor),
            code);
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
            .orElseThrow(() -> new NotFoundException("MFA challenge not found: " + request.challengeId()));
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
        boolean valid = switch (challenge.getFactor().getType()) {
            case TOTP -> verifyTotp(challenge.getFactor().getSecret(), request.code(), Instant.now());
            case RECOVERY_CODE -> consumeRecoveryCode(challenge.getFactor(), request.code());
            case SMS, EMAIL, WEBAUTHN -> tokens.sha256(request.code()).equals(challenge.getCodeHash());
        };
        if (valid) {
            challenge.verify();
            challenge.getFactor().verify();
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
        UUID tenantId = user.getTenant() == null ? null : user.getTenant().getId();
        Set<String> groupCodes = user.getGroups().stream().map(UserGroup::getCode).collect(java.util.stream.Collectors.toSet());
        Set<String> roleCodes = user.getRoles().stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getEmail(),
            user.getMobile(),
            user.getStatus(),
            tenantId,
            organizationId,
            groupCodes,
            roleCodes);
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
            throw new IllegalArgumentException("Password must be at least " + minLength + " characters");
        }
        int maxLength = authenticationPolicyService.currentPasswordMaxLength();
        if (password.length() > maxLength) {
            throw new IllegalArgumentException("Password must be at most " + maxLength + " characters");
        }
        validatePasswordComplexity(password, authenticationPolicyService.currentPasswordComplexity());
        validateRepeatedChars(password, authenticationPolicyService.currentPasswordMaxRepeatedChars());
        validateUserInfoPassword(user, password);
        validateWeakPassword(password);
        validatePasswordExtensionRules(password);
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
            throw new IllegalArgumentException("Password cannot match the current password");
        }
        boolean reused = credentialHistories.findByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), CredentialType.PASSWORD).stream()
            .limit(historyCount)
            .anyMatch(history -> passwordEncoder.matches(rawPassword, history.getSecretHash()));
        if (reused) {
            throw new IllegalArgumentException("Password cannot reuse the last " + historyCount + " password(s)");
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
        return expiresInMinutes == null || expiresInMinutes <= 0 ? DEFAULT_PASSWORD_RESET_TICKET_MINUTES : expiresInMinutes;
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
            case "number-letter" -> require(hasDigit && hasLetter, "Password must contain both numbers and letters");
            case "number-upper" -> require(hasDigit && hasUpper, "Password must contain both numbers and uppercase letters");
            case "all" -> require(hasDigit && hasUpper && hasLower && hasSpecial, "Password must contain numbers, uppercase letters, lowercase letters and special characters");
            case "two" -> require(categories >= 2, "Password must contain at least two character categories");
            default -> require(categories >= 3, "Password must contain at least three character categories");
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
                throw new IllegalArgumentException("Password contains too many repeated characters");
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
            throw new IllegalArgumentException("Password cannot contain user profile information");
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
            throw new IllegalArgumentException("Password is too weak");
        }
    }

    private void validatePasswordExtensionRules(String password) {
        java.util.Set<String> rules = authenticationPolicyService.currentPasswordExtensionRules();
        if (rules.contains("serial-number") && containsAscendingRun(password, '0', '9')) {
            throw new IllegalArgumentException("Password cannot contain serial numbers");
        }
        if (rules.contains("serial-letter") && (containsAscendingRun(password.toLowerCase(), 'a', 'z'))) {
            throw new IllegalArgumentException("Password cannot contain serial letters");
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
            factor.getType() == MfaFactorType.TOTP && !factor.isVerified() ? provisioningUri(factor) : null);
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

    private String generateMfaCode() {
        String token = tokens.generateToken(4);
        int numeric = Math.floorMod(token.hashCode(), 1_000_000);
        return String.format("%06d", numeric);
    }

    private String deliveryHint(MfaFactor factor) {
        return switch (factor.getType()) {
            case SMS -> "sms";
            case EMAIL -> "email";
            case TOTP -> "authenticator";
            case WEBAUTHN -> "webauthn";
            case RECOVERY_CODE -> "recovery-code";
        };
    }

    private String generateRecoveryCode() {
        String raw = tokens.generateToken(9)
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase();
        String padded = (raw + "ABCDEFGHJKLMNPQRSTUVWXYZ23456789").substring(0, 12);
        return padded.substring(0, 4) + "-" + padded.substring(4, 8) + "-" + padded.substring(8, 12);
    }

    private boolean consumeRecoveryCode(MfaFactor factor, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String hash = tokens.sha256(code.toUpperCase());
        List<String> remaining = factor.getSecret() == null || factor.getSecret().isBlank()
            ? List.of()
            : java.util.Arrays.stream(factor.getSecret().split("\\R"))
                .filter(item -> !item.isBlank())
                .toList();
        if (!remaining.contains(hash)) {
            return false;
        }
        factor.replaceSecret(remaining.stream()
            .filter(item -> !item.equals(hash))
            .collect(java.util.stream.Collectors.joining("\n")));
        return true;
    }

    private String generateTotpSecret() {
        byte[] bytes = new byte[20];
        SECURE_RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    private boolean verifyTotp(String secret, String code, Instant now) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long counter = now.getEpochSecond() / TOTP_STEP_SECONDS;
        for (int offset = -TOTP_WINDOW; offset <= TOTP_WINDOW; offset++) {
            if (code.equals(totp(secret, counter + offset))) {
                return true;
            }
        }
        return false;
    }

    private String totp(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to verify TOTP code", ex);
        }
    }

    private String provisioningUri(MfaFactor factor) {
        String label = url("Ant IAM:" + factor.getUser().getUsername());
        String issuer = url("Ant IAM");
        return "otpauth://totp/" + label + "?secret=" + factor.getSecret() + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String base32(byte[] bytes) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder encoded = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                encoded.append(alphabet.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            encoded.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return encoded.toString();
    }

    private byte[] base32Decode(String value) {
        String normalized = value.replace(" ", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        for (char ch : normalized.toCharArray()) {
            int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(ch);
            if (index < 0) {
                throw new IllegalArgumentException("Invalid TOTP secret");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }
}
