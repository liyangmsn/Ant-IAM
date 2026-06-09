package com.antiam.service;

import static com.antiam.dto.PolicyDtos.AuthenticationPolicyResponse;
import static com.antiam.dto.PolicyDtos.AuthenticationPolicyDecisionResponse;
import static com.antiam.dto.PolicyDtos.CreateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.EvaluateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.UpdateAuthenticationPolicyRequest;
import static com.antiam.dto.RiskDtos.EvaluateRiskRequest;

import com.antiam.domain.AuthenticationPolicy;
import com.antiam.common.NotFoundException;
import com.antiam.dto.RiskDtos.RiskAssessmentResponse;
import com.antiam.repository.AuthenticationPolicyRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationPolicyService {
    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;
    private static final int DEFAULT_PASSWORD_MAX_FAILURE_ATTEMPTS = 5;

    private final AuthenticationPolicyRepository policies;
    private final UserAccountRepository users;
    private final MfaFactorRepository mfaFactors;
    private final RiskService riskService;
    private final AuditService auditService;

    @Transactional
    // 创建认证策略，定义 MFA、密码和风险分级处理要求。
    public AuthenticationPolicyResponse create(CreateAuthenticationPolicyRequest request, String actor) {
        AuthenticationPolicy saved = policies.save(new AuthenticationPolicy(
            request.code(),
            request.name(),
            request.priority(),
            request.mfaRequired(),
            request.mfaEnrollmentRequired(),
            normalizePasswordMinLength(request.passwordMinLength()),
            request.stepUpRiskLevel(),
            request.denyRiskLevel()));
        saved.updatePasswordFailurePolicy(normalizeMaxFailureAttempts(request.passwordMaxFailureAttempts()));
        saved.updatePasswordExpiryPolicy(normalizePasswordExpiresInDays(request.passwordExpiresInDays()));
        saved.updatePasswordHistoryPolicy(normalizePasswordHistoryCount(request.passwordHistoryCount()));
        auditService.record(actor, "auth_policy.create", "auth_policy", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    // 更新认证策略配置，包含 MFA、密码策略和风险阈值。
    public AuthenticationPolicyResponse update(UUID policyId, UpdateAuthenticationPolicyRequest request, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policy.update(
            request.name(),
            request.priority(),
            request.mfaRequired(),
            request.mfaEnrollmentRequired(),
            normalizePasswordMinLength(request.passwordMinLength()),
            request.stepUpRiskLevel(),
            request.denyRiskLevel());
        policy.updatePasswordFailurePolicy(normalizeMaxFailureAttempts(request.passwordMaxFailureAttempts()));
        policy.updatePasswordExpiryPolicy(normalizePasswordExpiresInDays(request.passwordExpiresInDays()));
        policy.updatePasswordHistoryPolicy(normalizePasswordHistoryCount(request.passwordHistoryCount()));
        auditService.record(actor, "auth_policy.update", "auth_policy", policyId.toString(), policy.getCode());
        return toResponse(policy);
    }

    @Transactional
    // 启用认证策略，使其参与后续认证评估。
    public AuthenticationPolicyResponse enable(UUID policyId, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policy.enable();
        auditService.record(actor, "auth_policy.enable", "auth_policy", policyId.toString(), policy.getCode());
        return toResponse(policy);
    }

    @Transactional
    // 停用认证策略，保留策略配置和审计历史。
    public AuthenticationPolicyResponse disable(UUID policyId, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policy.disable();
        auditService.record(actor, "auth_policy.disable", "auth_policy", policyId.toString(), policy.getCode());
        return toResponse(policy);
    }

    @Transactional(readOnly = true)
    // 查询全部认证策略。
    public List<AuthenticationPolicyResponse> list() {
        return policies.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 评估当前应执行的认证动作，结合 MFA 注册状态和风险评估结果。
    public AuthenticationPolicyDecisionResponse evaluate(EvaluateAuthenticationPolicyRequest request) {
        users.findById(request.userId()).orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        AuthenticationPolicy policy = policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No enabled authentication policy found"));
        boolean userHasMfa = mfaFactors.existsByUserIdAndEnabledTrue(request.userId());
        RiskAssessmentResponse risk = riskService.evaluate(new EvaluateRiskRequest(
            request.userId(),
            request.ipAddress(),
            request.userAgent(),
            request.deviceFingerprint(),
            request.geoLocation()));
        boolean deny = atLeast(risk.riskLevel(), policy.getDenyRiskLevel());
        boolean stepUp = atLeast(risk.riskLevel(), policy.getStepUpRiskLevel());
        boolean mfaRequired = policy.isMfaRequired() || stepUp;
        boolean enrollmentRequired = policy.isMfaEnrollmentRequired() || (mfaRequired && !userHasMfa);
        String decision = deny ? "DENY"
            : enrollmentRequired && !userHasMfa ? "MFA_ENROLLMENT_REQUIRED"
            : mfaRequired ? "MFA_REQUIRED"
            : "ALLOW";
        return new AuthenticationPolicyDecisionResponse(
            policy.getId(),
            policy.getCode(),
            mfaRequired,
            policy.isMfaEnrollmentRequired(),
            userHasMfa,
            policy.getPasswordMinLength(),
            policy.getPasswordMaxFailureAttempts(),
            policy.getPasswordExpiresInDays(),
            policy.getPasswordHistoryCount(),
            policy.getStepUpRiskLevel(),
            policy.getDenyRiskLevel(),
            risk.riskLevel(),
            decision);
    }

    @Transactional(readOnly = true)
    // 获取当前生效密码最小长度，供密码设置和修改流程校验。
    public int currentPasswordMinLength() {
        return policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordMinLength)
            .orElse(DEFAULT_PASSWORD_MIN_LENGTH);
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码失败锁定阈值。
    public int currentPasswordMaxFailureAttempts() {
        return policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordMaxFailureAttempts)
            .map(this::normalizeMaxFailureAttempts)
            .orElse(DEFAULT_PASSWORD_MAX_FAILURE_ATTEMPTS);
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码过期天数，0 表示不过期。
    public int currentPasswordExpiresInDays() {
        return policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordExpiresInDays)
            .map(this::normalizePasswordExpiresInDays)
            .orElse(0);
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码历史数量，用于防止重复使用旧密码。
    public int currentPasswordHistoryCount() {
        return policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordHistoryCount)
            .map(this::normalizePasswordHistoryCount)
            .orElse(0);
    }

    private AuthenticationPolicy getPolicy(UUID policyId) {
        return policies.findById(policyId)
            .orElseThrow(() -> new NotFoundException("Authentication policy not found: " + policyId));
    }

    private AuthenticationPolicyResponse toResponse(AuthenticationPolicy policy) {
        return new AuthenticationPolicyResponse(
            policy.getId(),
            policy.getCode(),
            policy.getName(),
            policy.getPriority(),
            policy.isMfaRequired(),
            policy.isMfaEnrollmentRequired(),
            policy.getStepUpRiskLevel(),
            policy.getDenyRiskLevel(),
            policy.getPasswordMinLength(),
            policy.getPasswordMaxFailureAttempts(),
            policy.getPasswordExpiresInDays(),
            policy.getPasswordHistoryCount(),
            policy.isEnabled());
    }

    private int normalizeMaxFailureAttempts(int value) {
        return value <= 0 ? DEFAULT_PASSWORD_MAX_FAILURE_ATTEMPTS : value;
    }

    private int normalizeMaxFailureAttempts(Integer value) {
        return value == null ? DEFAULT_PASSWORD_MAX_FAILURE_ATTEMPTS : normalizeMaxFailureAttempts(value.intValue());
    }

    private int normalizePasswordMinLength(Integer value) {
        return value == null || value < DEFAULT_PASSWORD_MIN_LENGTH ? DEFAULT_PASSWORD_MIN_LENGTH : value;
    }

    private int normalizePasswordExpiresInDays(int value) {
        return Math.max(value, 0);
    }

    private int normalizePasswordExpiresInDays(Integer value) {
        return value == null ? 0 : normalizePasswordExpiresInDays(value.intValue());
    }

    private int normalizePasswordHistoryCount(int value) {
        return Math.max(value, 0);
    }

    private int normalizePasswordHistoryCount(Integer value) {
        return value == null ? 0 : normalizePasswordHistoryCount(value.intValue());
    }

    private boolean atLeast(com.antiam.domain.RiskLevel actual, com.antiam.domain.RiskLevel threshold) {
        return score(actual) >= score(threshold);
    }

    private int score(com.antiam.domain.RiskLevel level) {
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }
}
