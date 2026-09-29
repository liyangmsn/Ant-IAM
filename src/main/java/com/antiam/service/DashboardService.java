package com.antiam.service;

import static com.antiam.dto.DashboardDtos.ApplicationRankingResponse;
import static com.antiam.dto.DashboardDtos.AuthenticationMethodMetricResponse;
import static com.antiam.dto.DashboardDtos.DashboardMetricsResponse;
import static com.antiam.dto.DashboardDtos.DashboardStatisticsResponse;
import static com.antiam.dto.DashboardDtos.DashboardSummaryResponse;
import static com.antiam.dto.DashboardDtos.LoginLocationMetricResponse;
import static com.antiam.dto.DashboardDtos.RecentRiskAssessmentResponse;
import static com.antiam.dto.DashboardDtos.RecentSyncRunResponse;
import static com.antiam.dto.DashboardDtos.TrendPointResponse;

import com.antiam.domain.AccountStatus;
import com.antiam.domain.ApplicationAccessRequestStatus;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationMethods;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.DashboardRange;
import com.antiam.domain.IdentitySyncRun;
import com.antiam.domain.IdentitySyncRunStatus;
import com.antiam.domain.RiskAssessment;
import com.antiam.domain.RiskLevel;
import com.antiam.repository.ApplicationAccessRequestRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.AuthenticationEventRepository;
import com.antiam.repository.AuthenticationProviderRepository;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.IdentitySyncRunRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.RiskAssessmentRepository;
import com.antiam.repository.UserAccountRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final AuthenticationProviderRepository authenticationProviders;
    private final ClientMetadataService clientMetadataService;

    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final int RANKING_SIZE = 10;
    // 首页概览和分布图统计最近 30 天的数据，而不是只看最近 100 条记录。
    private static final java.time.Duration METRICS_WINDOW = java.time.Duration.ofDays(30);

    @Transactional(readOnly = true)
    // 汇总控制台首页核心指标，包括用户、应用、身份源、活跃会话和风险概览。
    public DashboardSummaryResponse summary() {
        Instant now = Instant.now();
        Instant from = now.minus(METRICS_WINDOW);
        return new DashboardSummaryResponse(
            users.count(),
            users.countByStatus(AccountStatus.ACTIVE),
            organizations.count(),
            applications.count(),
            identitySources.count(),
            sessions.countByActiveTrueAndExpiresAtAfter(now),
            authenticationEvents.countByCreatedAtGreaterThanEqual(from),
            countRisk(riskAssessments.countByRiskLevelSince(from)).get(RiskLevel.HIGH),
            countSyncRuns(syncRuns.countByStatusSince(from)).get(IdentitySyncRunStatus.FAILED),
            accessRequests.countByStatus(ApplicationAccessRequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    // 生成控制台图表所需的认证事件、风险等级和同步结果分布。
    public DashboardMetricsResponse metrics() {
        Instant from = Instant.now().minus(METRICS_WINDOW);
        return new DashboardMetricsResponse(
            countEvents(authenticationEvents.countByTypeSince(from)),
            countRisk(riskAssessments.countByRiskLevelSince(from)),
            countSyncRuns(syncRuns.countByStatusSince(from)));
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

    @Transactional(readOnly = true)
    // 按时间范围统计认证趋势、应用访问排名、认证方式和登录位置分布。
    public DashboardStatisticsResponse statistics(DashboardRange range) {
        Window window = window(range);
        List<AuthenticationEvent> events = authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(window.from());
        List<AuthenticationSession> rangeSessions = sessions.findByCreatedAtGreaterThanEqual(window.from());
        Instant startOfToday = ZonedDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<AuthenticationEvent> todayEvents = startOfToday.equals(window.from())
            ? events
            : authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(startOfToday);
        return new DashboardStatisticsResponse(
            range,
            countLogins(todayEvents),
            trend(events, range, window.buckets()),
            applicationRanking(rangeSessions),
            authenticationMethods(events),
            loginLocations(events));
    }

    private long countLogins(List<AuthenticationEvent> events) {
        return events.stream()
            .filter(event -> event.getType() == AuthenticationEventType.LOGIN_SUCCESS)
            .count();
    }

    // 按范围生成认证趋势的时间桶，桶内为登录成功次数。
    private List<TrendPointResponse> trend(List<AuthenticationEvent> events, DashboardRange range, List<Bucket> buckets) {
        Map<String, Long> counts = new HashMap<>();
        events.stream()
            .filter(event -> event.getType() == AuthenticationEventType.LOGIN_SUCCESS)
            .forEach(event -> counts.merge(bucketKey(range, event.getCreatedAt()), 1L, Long::sum));
        return buckets.stream()
            .map(bucket -> new TrendPointResponse(bucket.label(), counts.getOrDefault(bucket.key(), 0L)))
            .toList();
    }

    // 按应用统计范围内的认证会话数量，得到应用访问排名。
    private List<ApplicationRankingResponse> applicationRanking(List<AuthenticationSession> sessions) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        sessions.stream()
            .filter(session -> session.getApplication() != null)
            .forEach(session -> {
                UUID applicationId = session.getApplication().getId();
                names.putIfAbsent(applicationId, session.getApplication().getName());
                counts.merge(applicationId, 1L, Long::sum);
            });
        return counts.entrySet().stream()
            .map(entry -> new ApplicationRankingResponse(entry.getKey(), names.get(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparingLong(ApplicationRankingResponse::count).reversed())
            .limit(RANKING_SIZE)
            .toList();
    }

    // 按认证方式统计登录成功次数，认证方式名称优先取认证源配置的名称。
    private List<AuthenticationMethodMetricResponse> authenticationMethods(List<AuthenticationEvent> events) {
        Map<String, Long> counts = events.stream()
            .filter(event -> event.getType() == AuthenticationEventType.LOGIN_SUCCESS)
            .map(AuthenticationEvent::getMethod)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<String, String> labels = methodLabels();
        return counts.entrySet().stream()
            .map(entry -> new AuthenticationMethodMetricResponse(entry.getKey(), labels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparingLong(AuthenticationMethodMetricResponse::count).reversed())
            .toList();
    }

    private Map<String, String> methodLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(AuthenticationMethods.PASSWORD, "用户密码");
        labels.put(AuthenticationMethods.MOBILE_CODE, "短信快捷认证");
        authenticationProviders.findAll().forEach(provider -> labels.put(provider.getProviderKey(), provider.getName()));
        return labels;
    }

    // 按 IP 归属位置统计登录成功次数。
    private List<LoginLocationMetricResponse> loginLocations(List<AuthenticationEvent> events) {
        Map<String, Long> counts = events.stream()
            .filter(event -> event.getType() == AuthenticationEventType.LOGIN_SUCCESS)
            .map(event -> clientMetadataService.location(event.getIpAddress()))
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
            .map(entry -> new LoginLocationMetricResponse(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(LoginLocationMetricResponse::count).reversed())
            .toList();
    }

    private Window window(DashboardRange range) {
        ZonedDateTime now = ZonedDateTime.now();
        return switch (range) {
            case TODAY -> {
                ZonedDateTime start = now.toLocalDate().atStartOfDay(now.getZone());
                List<Bucket> buckets = new ArrayList<>();
                for (int hour = 0; hour < 24; hour++) {
                    ZonedDateTime point = start.plusHours(hour);
                    buckets.add(new Bucket(point.format(HOUR_KEY), "%02d时".formatted(hour)));
                }
                yield new Window(start.toInstant(), buckets);
            }
            case WEEK -> new Window(
                now.toLocalDate().minusDays(6).atStartOfDay(now.getZone()).toInstant(),
                dayBuckets(now, 7));
            case MONTH -> new Window(
                now.toLocalDate().minusDays(29).atStartOfDay(now.getZone()).toInstant(),
                dayBuckets(now, 30));
            case YEAR -> {
                ZonedDateTime start = now.toLocalDate().withDayOfMonth(1).minusMonths(11).atStartOfDay(now.getZone());
                List<Bucket> buckets = new ArrayList<>();
                for (int month = 0; month < 12; month++) {
                    ZonedDateTime point = start.plusMonths(month);
                    buckets.add(new Bucket(point.format(MONTH_KEY), point.format(MONTH_KEY)));
                }
                yield new Window(start.toInstant(), buckets);
            }
        };
    }

    private List<Bucket> dayBuckets(ZonedDateTime now, int days) {
        ZonedDateTime start = now.toLocalDate().minusDays(days - 1L).atStartOfDay(now.getZone());
        List<Bucket> buckets = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            ZonedDateTime point = start.plusDays(day);
            buckets.add(new Bucket(point.format(DAY_KEY), point.format(DAY_LABEL)));
        }
        return buckets;
    }

    private String bucketKey(DashboardRange range, Instant instant) {
        ZonedDateTime zoned = instant.atZone(ZoneId.systemDefault());
        return switch (range) {
            case TODAY -> zoned.format(HOUR_KEY);
            case WEEK, MONTH -> zoned.format(DAY_KEY);
            case YEAR -> zoned.format(MONTH_KEY);
        };
    }

    private record Bucket(String key, String label) {
    }

    private record Window(Instant from, List<Bucket> buckets) {
    }

    private Map<AuthenticationEventType, Long> countEvents(List<Object[]> rows) {
        return groupedCounts(AuthenticationEventType.class, rows);
    }

    private Map<RiskLevel, Long> countRisk(List<Object[]> rows) {
        return groupedCounts(RiskLevel.class, rows);
    }

    private Map<IdentitySyncRunStatus, Long> countSyncRuns(List<Object[]> rows) {
        return groupedCounts(IdentitySyncRunStatus.class, rows);
    }

    // 把 group by 查询的 [枚举, 数量] 结果转换为包含全部枚举值的计数表。
    private <E extends Enum<E>> Map<E, Long> groupedCounts(Class<E> type, List<Object[]> rows) {
        Map<E, Long> counts = new EnumMap<>(type);
        Arrays.stream(type.getEnumConstants()).forEach(value -> counts.put(value, 0L));
        rows.forEach(row -> {
            if (row[0] != null) {
                counts.put(type.cast(row[0]), ((Number) row[1]).longValue());
            }
        });
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
