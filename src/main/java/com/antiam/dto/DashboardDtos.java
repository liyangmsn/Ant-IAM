package com.antiam.dto;

import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.DashboardRange;
import com.antiam.domain.IdentitySyncRunStatus;
import com.antiam.domain.RiskLevel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {
    }

    public record DashboardSummaryResponse(
        long users,
        long activeUsers,
        long organizations,
        long applications,
        long identitySources,
        long activeSessions,
        long recentAuthenticationEvents,
        long highRiskAssessments,
        long failedSyncRuns,
        long pendingAccessRequests
    ) {
    }

    public record AuthenticationEventMetric(AuthenticationEventType type, long count) {
    }

    public record RiskMetric(RiskLevel level, long count) {
    }

    public record SyncRunMetric(IdentitySyncRunStatus status, long count) {
    }

    public record DashboardMetricsResponse(
        Map<AuthenticationEventType, Long> authenticationEvents,
        Map<RiskLevel, Long> riskAssessments,
        Map<IdentitySyncRunStatus, Long> syncRuns
    ) {
    }

    public record TrendPointResponse(String label, long count) {
    }

    public record ApplicationRankingResponse(UUID applicationId, String applicationName, long count) {
    }

    public record AuthenticationMethodMetricResponse(String method, String label, long count) {
    }

    public record LoginLocationMetricResponse(String location, long count) {
    }

    public record DashboardStatisticsResponse(
        DashboardRange range,
        long todayAuthentications,
        List<TrendPointResponse> authenticationTrend,
        List<ApplicationRankingResponse> applicationRanking,
        List<AuthenticationMethodMetricResponse> authenticationMethods,
        List<LoginLocationMetricResponse> loginLocations
    ) {
    }

    public record RecentRiskAssessmentResponse(
        UUID id,
        UUID userId,
        RiskLevel riskLevel,
        String decision,
        List<String> matchedRules
    ) {
    }

    public record RecentSyncRunResponse(
        UUID id,
        UUID syncJobId,
        IdentitySyncRunStatus status,
        String message
    ) {
    }
}
