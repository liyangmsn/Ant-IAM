package com.antiam.service;

import static com.antiam.dto.PolicyDtos.AuthenticationPolicyResponse;
import static com.antiam.dto.PolicyDtos.AuthenticationPolicyDecisionResponse;
import static com.antiam.dto.PolicyDtos.CreateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.EvaluateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.UpdateAuthenticationPolicyRequest;
import static com.antiam.dto.RiskDtos.EvaluateRiskRequest;

import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.domain.AuthenticationPolicy;
import com.antiam.domain.RiskLevel;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.AuthenticationPolicyMapper;
import com.antiam.repository.AuthenticationPolicyRepository;
import com.antiam.repository.MfaFactorRepository;
import com.antiam.repository.SystemSettingRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    private final SystemSettingRepository settings;
    private final UserAccountRepository users;
    private final MfaFactorRepository mfaFactors;
    private final AuthenticationPolicyMapper authenticationPolicyMapper;
    private final RiskService riskService;
    private final AuditService auditService;

    @Transactional
    // 创建认证策略，定义 MFA、密码和风险分级处理要求。
    public AuthenticationPolicyResponse create(CreateAuthenticationPolicyRequest request, String actor) {
        String code = request.code().trim();
        if (policies.findByCode(code).isPresent()) {
            throw new ConflictException("认证策略编码已存在: " + code);
        }
        validateRiskLevels(request.stepUpRiskLevel(), request.denyRiskLevel());
        AuthenticationPolicy saved = policies.save(new AuthenticationPolicy(
            code,
            request.name().trim(),
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
        return authenticationPolicyMapper.toResponse(saved);
    }

    @Transactional
    // 更新认证策略配置，包含 MFA、密码策略和风险阈值。
    public AuthenticationPolicyResponse update(UUID policyId, UpdateAuthenticationPolicyRequest request, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        validateRiskLevels(request.stepUpRiskLevel(), request.denyRiskLevel());
        policy.update(
            request.name().trim(),
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
        return authenticationPolicyMapper.toResponse(policy);
    }

    @Transactional
    // 启用认证策略，使其参与后续认证评估。
    public AuthenticationPolicyResponse enable(UUID policyId, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policy.enable();
        auditService.record(actor, "auth_policy.enable", "auth_policy", policyId.toString(), policy.getCode());
        return authenticationPolicyMapper.toResponse(policy);
    }

    @Transactional
    // 停用认证策略，保留策略配置和审计历史。
    public AuthenticationPolicyResponse disable(UUID policyId, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policy.disable();
        auditService.record(actor, "auth_policy.disable", "auth_policy", policyId.toString(), policy.getCode());
        return authenticationPolicyMapper.toResponse(policy);
    }

    @Transactional
    // 删除认证策略。
    public void delete(UUID policyId, String actor) {
        AuthenticationPolicy policy = getPolicy(policyId);
        policies.delete(policy);
        auditService.record(actor, "auth_policy.delete", "auth_policy", policyId.toString(), policy.getCode());
    }

    @Transactional(readOnly = true)
    // 查询全部认证策略。
    public List<AuthenticationPolicyResponse> list() {
        return policies.findAll().stream().map(authenticationPolicyMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 查询认证策略详情。
    public AuthenticationPolicyResponse get(UUID policyId) {
        return authenticationPolicyMapper.toResponse(getPolicy(policyId));
    }

    @Transactional
    // 评估当前应执行的认证动作，结合 MFA 注册状态和风险评估结果；没有启用策略时按内置默认规则判断。
    // 风险评估记录由 RiskService 落库，评估结论写入审计日志便于追溯。
    public AuthenticationPolicyDecisionResponse evaluate(EvaluateAuthenticationPolicyRequest request, String actor) {
        UserAccount user = users.findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        AuthenticationPolicy policy = activePolicy().orElse(null);
        boolean userHasMfa = hasLoginMfa(user);
        RiskLevel riskLevel = riskService.evaluate(new EvaluateRiskRequest(
            request.userId(),
            request.ipAddress(),
            request.userAgent(),
            request.deviceFingerprint(),
            request.geoLocation())).riskLevel();
        LoginDecision decision = decide(policy, riskLevel, userHasMfa);
        auditService.record(
            actor,
            "auth_policy.evaluate",
            "user",
            user.getId().toString(),
            "policy=" + (policy == null ? "default" : policy.getCode()) + ", riskLevel=" + riskLevel + ", decision=" + decision.decision());
        return new AuthenticationPolicyDecisionResponse(
            policy == null ? null : policy.getId(),
            policy == null ? null : policy.getCode(),
            decision.mfaRequired(),
            policy != null && policy.isMfaEnrollmentRequired(),
            userHasMfa,
            currentPasswordMinLength(),
            currentPasswordMaxFailureAttempts(),
            currentPasswordExpiresInDays(),
            currentPasswordHistoryCount(),
            policy == null ? RiskLevel.MEDIUM : policy.getStepUpRiskLevel(),
            policy == null ? null : policy.getDenyRiskLevel(),
            riskLevel,
            decision.decision());
    }

    @Transactional
    // 登录流程使用的策略判定：只在存在启用的风险规则时写入风险评估记录。
    public LoginDecision evaluateLogin(UserAccount user, String ipAddress, String userAgent, String geoLocation) {
        RiskLevel riskLevel = riskService.evaluateLogin(user, ipAddress, userAgent, geoLocation);
        return decide(activePolicy().orElse(null), riskLevel, hasLoginMfa(user));
    }

    /**
     * 用户是否拥有可用于登录二次验证的 MFA 因子。
     */
    @Transactional(readOnly = true)
    public boolean hasLoginMfa(UserAccount user) {
        return mfaFactors.findByUserId(user.getId()).stream().anyMatch(MfaVerificationService::isLoginCapable);
    }

    /**
     * 按策略和风险等级得出认证动作。没有启用策略时：高风险要求 MFA（无 MFA 则拒绝），中风险在有 MFA 时要求验证。
     */
    static LoginDecision decide(AuthenticationPolicy policy, RiskLevel riskLevel, boolean userHasMfa) {
        RiskLevel level = riskLevel == null ? RiskLevel.LOW : riskLevel;
        if (policy == null) {
            return switch (level) {
                case HIGH -> userHasMfa ? LoginDecision.of(LoginDecision.MFA_REQUIRED, level) : LoginDecision.of(LoginDecision.DENY, level);
                case MEDIUM -> userHasMfa ? LoginDecision.of(LoginDecision.MFA_REQUIRED, level) : LoginDecision.of(LoginDecision.ALLOW, level);
                case LOW -> LoginDecision.of(LoginDecision.ALLOW, level);
            };
        }
        if (policy.getDenyRiskLevel() != null && atLeast(level, policy.getDenyRiskLevel())) {
            return LoginDecision.of(LoginDecision.DENY, level);
        }
        boolean stepUp = policy.getStepUpRiskLevel() != null && atLeast(level, policy.getStepUpRiskLevel());
        boolean mfaRequired = policy.isMfaRequired() || stepUp;
        if (mfaRequired && userHasMfa) {
            return LoginDecision.of(LoginDecision.MFA_REQUIRED, level);
        }
        if ((mfaRequired || policy.isMfaEnrollmentRequired()) && !userHasMfa) {
            return LoginDecision.of(LoginDecision.MFA_ENROLLMENT_REQUIRED, level);
        }
        return LoginDecision.of(LoginDecision.ALLOW, level);
    }

    @Transactional(readOnly = true)
    // 获取当前生效密码最小长度，供密码设置和修改流程校验。
    public int currentPasswordMinLength() {
        return intSetting("security.password.min_length")
            .map(this::normalizePasswordMinLength)
            .orElseGet(() -> policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordMinLength)
            .orElse(DEFAULT_PASSWORD_MIN_LENGTH));
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码失败锁定阈值。
    public int currentPasswordMaxFailureAttempts() {
        return intSetting("security.general.login_failure_max_attempts")
            .map(this::normalizeMaxFailureAttempts)
            .orElseGet(() -> policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordMaxFailureAttempts)
            .map(this::normalizeMaxFailureAttempts)
            .orElse(DEFAULT_PASSWORD_MAX_FAILURE_ATTEMPTS));
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码过期天数，0 表示不过期。
    public int currentPasswordExpiresInDays() {
        return intSetting("security.password.expires_in_days")
            .map(this::normalizePasswordExpiresInDays)
            .orElseGet(() -> policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordExpiresInDays)
            .map(this::normalizePasswordExpiresInDays)
            .orElse(0));
    }

    @Transactional(readOnly = true)
    // 获取当前生效的密码历史数量，用于防止重复使用旧密码。
    public int currentPasswordHistoryCount() {
        Optional<Boolean> historyCheckEnabled = booleanSetting("security.password.history_check_enabled");
        if (historyCheckEnabled.isPresent() && !historyCheckEnabled.get()) {
            return 0;
        }
        return intSetting("security.password.history_count")
            .map(this::normalizePasswordHistoryCount)
            .orElseGet(() -> policies.findByEnabledTrueOrderByPriorityAsc().stream()
            .findFirst()
            .map(AuthenticationPolicy::getPasswordHistoryCount)
            .map(this::normalizePasswordHistoryCount)
            .orElse(0));
    }

    @Transactional(readOnly = true)
    // 获取当前生效密码最大长度，供密码设置和修改流程校验。
    public int currentPasswordMaxLength() {
        return intSetting("security.password.max_length")
            .map(value -> Math.max(value, currentPasswordMinLength()))
            .orElse(20);
    }

    @Transactional(readOnly = true)
    // 获取当前密码复杂度要求。
    public String currentPasswordComplexity() {
        return stringSetting("security.password.complexity").orElse("three");
    }

    @Transactional(readOnly = true)
    // 获取当前允许连续出现的相同字符数量，0 表示不检查。
    public int currentPasswordMaxRepeatedChars() {
        return intSetting("security.password.max_repeated_chars")
            .map(value -> Math.max(value, 0))
            .orElse(3);
    }

    @Transactional(readOnly = true)
    // 是否禁止密码包含用户资料。
    public boolean currentPasswordCheckUserInfo() {
        return booleanSetting("security.password.check_user_info").orElse(true);
    }

    @Transactional(readOnly = true)
    // 是否启用非法字符序列检查（键盘相邻、连续字母或数字）。
    public boolean currentPasswordIllegalSequenceCheckEnabled() {
        return booleanSetting("security.password.illegal_sequence_check_enabled").orElse(false);
    }

    @Transactional(readOnly = true)
    // 是否启用弱密码检查。
    public boolean currentPasswordWeakPasswordCheckEnabled() {
        return booleanSetting("security.password.weak_password_check_enabled").orElse(true);
    }

    @Transactional(readOnly = true)
    // 获取额外弱密码列表。
    public Set<String> currentAdditionalWeakPasswords() {
        return stringSetting("security.password.additional_weak_passwords")
            .map(value -> (Set<String>) value.lines()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(String::toLowerCase)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new)))
            .orElseGet(java.util.Collections::emptySet);
    }

    @Transactional(readOnly = true)
    // 获取密码扩展规则，例如禁止连续数字或连续字母。
    public Set<String> currentPasswordExtensionRules() {
        return stringSetting("security.password.extension_rules")
            .map(value -> (Set<String>) java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new)))
            .orElseGet(java.util.Collections::emptySet);
    }

    private Optional<Integer> intSetting(String key) {
        return settings.findBySettingKey(key)
            .map(setting -> {
                try {
                    return Integer.valueOf(setting.getSettingValue());
                } catch (NumberFormatException ex) {
                    return null;
                }
            });
    }

    private Optional<Boolean> booleanSetting(String key) {
        return settings.findBySettingKey(key)
            .map(setting -> Boolean.valueOf(setting.getSettingValue()));
    }

    private Optional<String> stringSetting(String key) {
        return settings.findBySettingKey(key)
            .map(setting -> setting.getSettingValue() == null ? "" : setting.getSettingValue())
            .filter(value -> !value.isBlank());
    }

    private Optional<AuthenticationPolicy> activePolicy() {
        return policies.findByEnabledTrueOrderByPriorityAsc().stream().findFirst();
    }

    private void validateRiskLevels(RiskLevel stepUpRiskLevel, RiskLevel denyRiskLevel) {
        if (stepUpRiskLevel != null && denyRiskLevel != null && score(stepUpRiskLevel) > score(denyRiskLevel)) {
            throw new IllegalArgumentException("增强认证风险等级不能高于拒绝风险等级");
        }
    }

    private AuthenticationPolicy getPolicy(UUID policyId) {
        return policies.findById(policyId)
            .orElseThrow(() -> new NotFoundException("Authentication policy not found: " + policyId));
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

    private static boolean atLeast(RiskLevel actual, RiskLevel threshold) {
        return score(actual) >= score(threshold);
    }

    private static int score(RiskLevel level) {
        if (level == null) {
            return 0;
        }
        return switch (level) {
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    /**
     * 登录策略判定结果。
     */
    public record LoginDecision(String decision, RiskLevel riskLevel) {
        public static final String ALLOW = "ALLOW";
        public static final String DENY = "DENY";
        public static final String MFA_REQUIRED = "MFA_REQUIRED";
        public static final String MFA_ENROLLMENT_REQUIRED = "MFA_ENROLLMENT_REQUIRED";

        static LoginDecision of(String decision, RiskLevel riskLevel) {
            return new LoginDecision(decision, riskLevel);
        }

        public boolean mfaRequired() {
            return MFA_REQUIRED.equals(decision) || MFA_ENROLLMENT_REQUIRED.equals(decision);
        }
    }
}
