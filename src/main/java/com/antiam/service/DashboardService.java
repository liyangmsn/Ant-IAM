package com.antiam.service;

import static com.antiam.dto.DashboardDtos.DashboardMetricsResponse;
import static com.antiam.dto.DashboardDtos.DashboardSummaryResponse;
import static com.antiam.dto.DashboardDtos.RecentRiskAssessmentResponse;
import static com.antiam.dto.DashboardDtos.RecentSyncRunResponse;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.ApplicationAccessRequestStatus;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.IdentitySyncRun;
import com.antiam.domain.IdentitySyncRunStatus;
import com.antiam.domain.RiskAssessment;
import com.antiam.domain.RiskLevel;
import com.antiam.repository.ApplicationAccessRequestRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.IdentitySyncRunRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.RiskAssessmentRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserAccountRepository users;
    private final OrganizationRepository organizations;
    private final ApplicationRepository applications;
    private final IdentitySourceRepository identitySources;
    private final AuthenticationSessionRepository sessions;
    private final AuthenticationEventRepository authenticationEvents;
    private final RiskAssessmentRepository riskAssessments;
    private final IdentitySyncRunRepository syncRuns;
    private final ApplicationAccessRequestRepository accessRequests;

    @Transactional(readOnly = true)
    // 汇总控制台首页核心指标，包括用户、应用、身份源、活跃会话和风险概览。
    public DashboardSummaryResponse summary() {
        List<AuthenticationSession> allSessions = sessions.findAll();
        List<AuthenticationEvent> recentEvents = authenticationEvents.findTop100ByOrderByCreatedAtDesc();
        List<RiskAssessment> recentRisk = riskAssessments.findTop100ByOrderByCreatedAtDesc();
        List<IdentitySyncRun> recentSyncRuns = syncRuns.findTop100ByOrderByCreatedAtDesc();
        return new DashboardSummaryResponse(
            users.count(),
            users.countByStatus(AccountStatus.ACTIVE),
            organizations.count(),
            applications.count(),
            identitySources.count(),
            allSessions.stream().filter(AuthenticationSession::isActive).count(),
            recentEvents.size(),
            recentRisk.stream().filter(item -> item.getRiskLevel() == RiskLevel.HIGH).count(),
            recentSyncRuns.stream().filter(item -> item.getStatus() == IdentitySyncRunStatus.FAILED).count(),
            accessRequests.countByStatus(ApplicationAccessRequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    // 生成控制台图表所需的认证事件、风险等级和同步结果分布。
    public DashboardMetricsResponse metrics() {
        return new DashboardMetricsResponse(
            countEvents(authenticationEvents.findTop100ByOrderByCreatedAtDesc()),
            countRisk(riskAssessments.findTop100ByOrderByCreatedAtDesc()),
            countSyncRuns(syncRuns.findTop100ByOrderByCreatedAtDesc()));
    }

    @Transactional(readOnly = true)
    // 查询最近风险评估记录，供控制台风险动态展示。
    public List<RecentRiskAssessmentResponse> recentRiskAssessments() {
        return riskAssessments.findTop100ByOrderByCreatedAtDesc().stream()
            .limit(20)
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询最近身份源同步运行记录，供控制台同步动态展示。
    public List<RecentSyncRunResponse> recentSyncRuns() {
        return syncRuns.findTop100ByOrderByCreatedAtDesc().stream()
            .limit(20)
            .map(this::toResponse)
            .toList();
    }

    private Map<AuthenticationEventType, Long> countEvents(List<AuthenticationEvent> values) {
        Map<AuthenticationEventType, Long> counts = new EnumMap<>(AuthenticationEventType.class);
        Arrays.stream(AuthenticationEventType.values()).forEach(type -> counts.put(type, 0L));
        values.forEach(event -> counts.compute(event.getType(), (key, count) -> count == null ? 1L : count + 1));
        return counts;
    }

    private Map<RiskLevel, Long> countRisk(List<RiskAssessment> values) {
        Map<RiskLevel, Long> counts = new EnumMap<>(RiskLevel.class);
        Arrays.stream(RiskLevel.values()).forEach(level -> counts.put(level, 0L));
        values.forEach(assessment -> counts.compute(assessment.getRiskLevel(), (key, count) -> count == null ? 1L : count + 1));
        return counts;
    }

    private Map<IdentitySyncRunStatus, Long> countSyncRuns(List<IdentitySyncRun> values) {
        Map<IdentitySyncRunStatus, Long> counts = new EnumMap<>(IdentitySyncRunStatus.class);
        Arrays.stream(IdentitySyncRunStatus.values()).forEach(status -> counts.put(status, 0L));
        values.forEach(run -> counts.compute(run.getStatus(), (key, count) -> count == null ? 1L : count + 1));
        return counts;
    }

    private RecentRiskAssessmentResponse toResponse(RiskAssessment assessment) {
        List<String> matchedRules = assessment.getMatchedRules() == null || assessment.getMatchedRules().isBlank()
            ? List.of()
            : Arrays.asList(assessment.getMatchedRules().split(","));
        return new RecentRiskAssessmentResponse(
            assessment.getId(),
            assessment.getUser().getId(),
            assessment.getRiskLevel(),
            assessment.getDecision(),
            matchedRules);
    }

    private RecentSyncRunResponse toResponse(IdentitySyncRun run) {
        return new RecentSyncRunResponse(
            run.getId(),
            run.getSyncJob().getId(),
            run.getStatus(),
            run.getMessage());
    }
}
