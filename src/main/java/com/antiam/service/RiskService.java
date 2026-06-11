package com.antiam.service;

import static com.antiam.dto.RiskDtos.CreateRiskRuleRequest;
import static com.antiam.dto.RiskDtos.EvaluateRiskRequest;
import static com.antiam.dto.RiskDtos.RiskAssessmentResponse;
import static com.antiam.dto.RiskDtos.RiskRuleResponse;
import static com.antiam.dto.RiskDtos.UpdateRiskRuleRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.RiskAssessment;
import com.antiam.domain.RiskLevel;
import com.antiam.domain.RiskRule;
import com.antiam.domain.RiskRuleType;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.RiskMapper;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.RiskAssessmentRepository;
import com.antiam.repository.RiskRuleRepository;
import com.antiam.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRuleRepository rules;
    private final RiskAssessmentRepository assessments;
    private final UserAccountRepository users;
    private final AuthenticationEventRepository authenticationEvents;
    private final RiskMapper riskMapper;
    private final AuditService auditService;

    @Transactional
    // 创建风险规则，规则会在风险评估时按启用状态参与匹配。
    public RiskRuleResponse createRule(CreateRiskRuleRequest request, String actor) {
        RiskRule saved = rules.save(new RiskRule(
            request.code(),
            request.name(),
            request.type(),
            request.conditionValue(),
            request.threshold(),
            request.riskLevel()));
        auditService.record(actor, "risk_rule.create", "risk_rule", saved.getId().toString(), saved.getCode());
        return riskMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询风险规则列表，支持类型、启用状态和关键字过滤。
    public List<RiskRuleResponse> listRules(RiskRuleType type, Boolean enabled, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return rules.findAll().stream()
            .filter(rule -> type == null || rule.getType() == type)
            .filter(rule -> enabled == null || rule.isEnabled() == enabled)
            .filter(rule -> normalizedKeyword == null || matchesKeyword(rule, normalizedKeyword))
            .map(riskMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询风险规则详情。
    public RiskRuleResponse getRule(UUID ruleId) {
        return riskMapper.toResponse(getRuleEntity(ruleId));
    }

    @Transactional
    // 更新风险规则的匹配条件、阈值和风险等级。
    public RiskRuleResponse updateRule(UUID ruleId, UpdateRiskRuleRequest request, String actor) {
        RiskRule rule = getRuleEntity(ruleId);
        rule.update(request.name(), request.type(), request.conditionValue(), request.threshold(), request.riskLevel());
        auditService.record(actor, "risk_rule.update", "risk_rule", ruleId.toString(), rule.getCode());
        return riskMapper.toResponse(rule);
    }

    @Transactional
    // 启用风险规则，使其参与后续风险评估。
    public RiskRuleResponse enableRule(UUID ruleId, String actor) {
        RiskRule rule = getRuleEntity(ruleId);
        rule.enable();
        auditService.record(actor, "risk_rule.enable", "risk_rule", ruleId.toString(), rule.getCode());
        return riskMapper.toResponse(rule);
    }

    @Transactional
    // 停用风险规则，保留规则配置和历史评估结果。
    public RiskRuleResponse disableRule(UUID ruleId, String actor) {
        RiskRule rule = getRuleEntity(ruleId);
        rule.disable();
        auditService.record(actor, "risk_rule.disable", "risk_rule", ruleId.toString(), rule.getCode());
        return riskMapper.toResponse(rule);
    }

    @Transactional
    // 根据 IP、设备、地理位置和失败登录次数等上下文执行风险评估。
    public RiskAssessmentResponse evaluate(EvaluateRiskRequest request) {
        UserAccount user = users.findById(request.userId())
            .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        List<RiskRule> matched = rules.findByEnabledTrueOrderByCreatedAtAsc().stream()
            .filter(rule -> matches(rule, request))
            .toList();
        RiskLevel level = highest(matched);
        String decision = switch (level) {
            case HIGH -> "DENY_OR_STEP_UP";
            case MEDIUM -> "STEP_UP_MFA";
            case LOW -> "ALLOW";
        };
        RiskAssessment saved = assessments.save(new RiskAssessment(
            user,
            request.ipAddress(),
            request.userAgent(),
            request.deviceFingerprint(),
            request.geoLocation(),
            level,
            matched.stream().map(RiskRule::getCode).collect(java.util.stream.Collectors.joining(",")),
            decision));
        authenticationEvents.save(new AuthenticationEvent(
            null,
            user,
            null,
            AuthenticationEventType.RISK_ASSESSED,
            request.ipAddress(),
            request.userAgent(),
            "level=" + level
                + ";decision=" + decision
                + ";matchedRules=" + saved.getMatchedRules()
                + ";deviceFingerprint=" + nullToEmpty(request.deviceFingerprint())
                + ";geoLocation=" + nullToEmpty(request.geoLocation())));
        return riskMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询风险评估历史，支持用户、风险等级、决策和关键字过滤。
    public List<RiskAssessmentResponse> searchAssessments(
        UUID userId,
        RiskLevel riskLevel,
        String decision,
        String keyword,
        Integer limit
    ) {
        String normalizedDecision = normalizeKeyword(decision);
        String normalizedKeyword = normalizeKeyword(keyword);
        int cappedLimit = limit == null ? 100 : Math.clamp(limit, 1, 500);
        return assessments.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
            .filter(assessment -> userId == null || assessment.getUser().getId().equals(userId))
            .filter(assessment -> riskLevel == null || assessment.getRiskLevel() == riskLevel)
            .filter(assessment -> normalizedDecision == null || contains(assessment.getDecision(), normalizedDecision))
            .filter(assessment -> normalizedKeyword == null || matchesAssessmentKeyword(assessment, normalizedKeyword))
            .limit(cappedLimit)
            .map(riskMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询单次风险评估详情。
    public RiskAssessmentResponse getAssessment(UUID assessmentId) {
        return assessments.findById(assessmentId)
            .map(riskMapper::toResponse)
            .orElseThrow(() -> new NotFoundException("Risk assessment not found: " + assessmentId));
    }

    private boolean matches(RiskRule rule, EvaluateRiskRequest request) {
        return switch (rule.getType()) {
            case IP_CONTAINS -> contains(request.ipAddress(), rule.getConditionValue());
            case USER_AGENT_CONTAINS -> contains(request.userAgent(), rule.getConditionValue());
            case FAILED_LOGIN_COUNT -> failedLoginCount(request.userId()) >= rule.getThreshold();
            case DEVICE_FINGERPRINT_CONTAINS -> contains(request.deviceFingerprint(), rule.getConditionValue());
            case DEVICE_FINGERPRINT_CHANGED -> deviceChanged(request);
            case GEO_LOCATION_NOT_ALLOWED -> geoLocationNotAllowed(request.geoLocation(), rule.getConditionValue());
        };
    }

    private boolean deviceChanged(EvaluateRiskRequest request) {
        if (request.deviceFingerprint() == null || request.deviceFingerprint().isBlank()) {
            return false;
        }
        return assessments.findFirstByUserIdAndDeviceFingerprintIsNotNullOrderByCreatedAtDesc(request.userId())
            .map(previous -> !request.deviceFingerprint().equals(previous.getDeviceFingerprint()))
            .orElse(false);
    }

    private boolean geoLocationNotAllowed(String geoLocation, String allowedLocations) {
        if (geoLocation == null || geoLocation.isBlank() || allowedLocations == null || allowedLocations.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(allowedLocations.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .noneMatch(value -> value.equalsIgnoreCase(geoLocation));
    }

    private long failedLoginCount(java.util.UUID userId) {
        return authenticationEvents.countByUserIdAndTypeAndCreatedAtAfter(
            userId,
            AuthenticationEventType.LOGIN_FAILURE,
            Instant.now().minus(1, ChronoUnit.HOURS));
    }

    private boolean contains(String value, String expected) {
        return value != null && expected != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesKeyword(RiskRule rule, String keyword) {
        return contains(rule.getCode(), keyword)
            || contains(rule.getName(), keyword)
            || contains(rule.getConditionValue(), keyword)
            || rule.getType().name().toLowerCase().contains(keyword)
            || rule.getRiskLevel().name().toLowerCase().contains(keyword);
    }

    private boolean matchesAssessmentKeyword(RiskAssessment assessment, String keyword) {
        return contains(assessment.getUser().getUsername(), keyword)
            || contains(assessment.getUser().getDisplayName(), keyword)
            || contains(assessment.getIpAddress(), keyword)
            || contains(assessment.getUserAgent(), keyword)
            || contains(assessment.getDeviceFingerprint(), keyword)
            || contains(assessment.getGeoLocation(), keyword)
            || contains(assessment.getMatchedRules(), keyword)
            || contains(assessment.getDecision(), keyword)
            || assessment.getRiskLevel().name().toLowerCase().contains(keyword);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private RiskLevel highest(List<RiskRule> matched) {
        List<RiskLevel> levels = new ArrayList<>(matched.stream().map(RiskRule::getRiskLevel).toList());
        if (levels.contains(RiskLevel.HIGH)) {
            return RiskLevel.HIGH;
        }
        if (levels.contains(RiskLevel.MEDIUM)) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private RiskRule getRuleEntity(UUID ruleId) {
        return rules.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("Risk rule not found: " + ruleId));
    }

}
