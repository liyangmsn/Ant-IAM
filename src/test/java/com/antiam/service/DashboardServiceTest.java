package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.Application;
import com.antiam.domain.AuthenticationEvent;
import com.antiam.domain.AuthenticationEventType;
import com.antiam.domain.AuthenticationMethods;
import com.antiam.domain.AuthenticationProvider;
import com.antiam.domain.AuthenticationSession;
import com.antiam.domain.DashboardRange;
import com.antiam.dto.DashboardDtos.ApplicationRankingResponse;
import com.antiam.dto.DashboardDtos.AuthenticationMethodMetricResponse;
import com.antiam.dto.DashboardDtos.DashboardStatisticsResponse;
import com.antiam.dto.DashboardDtos.LoginLocationMetricResponse;
import com.antiam.dto.DashboardDtos.TrendPointResponse;
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final IdentitySourceRepository identitySources = mock(IdentitySourceRepository.class);
    private final AuthenticationSessionRepository sessions = mock(AuthenticationSessionRepository.class);
    private final AuthenticationEventRepository authenticationEvents = mock(AuthenticationEventRepository.class);
    private final RiskAssessmentRepository riskAssessments = mock(RiskAssessmentRepository.class);
    private final IdentitySyncRunRepository syncRuns = mock(IdentitySyncRunRepository.class);
    private final ApplicationAccessRequestRepository accessRequests = mock(ApplicationAccessRequestRepository.class);
    private final AuthenticationProviderRepository authenticationProviders = mock(AuthenticationProviderRepository.class);
    private final ClientMetadataService clientMetadataService = mock(ClientMetadataService.class);

    private final DashboardService service = new DashboardService(
        users,
        organizations,
        applications,
        identitySources,
        sessions,
        authenticationEvents,
        riskAssessments,
        syncRuns,
        accessRequests,
        authenticationProviders,
        clientMetadataService);

    @Test
    void countsLoginsIntoHourlyTrendAndNamesAuthenticationMethods() {
        AuthenticationEvent passwordLogin = loginEvent(AuthenticationMethods.PASSWORD, "127.0.0.1", ZonedDateTime.now().withHour(10));
        AuthenticationEvent mobileLogin = loginEvent(AuthenticationMethods.MOBILE_CODE, "10.1.2.3", ZonedDateTime.now().withHour(10));
        when(authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of(passwordLogin, mobileLogin));
        when(sessions.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(authenticationProviders.findAll()).thenReturn(List.of());
        when(clientMetadataService.location("127.0.0.1")).thenReturn("本机");
        when(clientMetadataService.location("10.1.2.3")).thenReturn("内网");

        DashboardStatisticsResponse response = service.statistics(DashboardRange.TODAY);

        assertThat(response.range()).isEqualTo(DashboardRange.TODAY);
        assertThat(response.todayAuthentications()).isEqualTo(2);
        assertThat(response.authenticationTrend()).hasSize(24);
        assertThat(response.authenticationTrend()).contains(new TrendPointResponse("10时", 2));
        assertThat(response.authenticationMethods()).containsExactlyInAnyOrder(
            new AuthenticationMethodMetricResponse(AuthenticationMethods.PASSWORD, "用户密码", 1),
            new AuthenticationMethodMetricResponse(AuthenticationMethods.MOBILE_CODE, "短信快捷认证", 1));
        assertThat(response.loginLocations()).containsExactlyInAnyOrder(
            new LoginLocationMetricResponse("本机", 1),
            new LoginLocationMetricResponse("内网", 1));
    }

    @Test
    void usesConfiguredProviderNameForThirdPartyAuthenticationMethod() {
        AuthenticationProvider provider = mock(AuthenticationProvider.class);
        when(provider.getProviderKey()).thenReturn("wechat");
        when(provider.getName()).thenReturn("微信认证");
        AuthenticationEvent thirdPartyLogin = loginEvent("wechat", "127.0.0.1", ZonedDateTime.now());
        when(authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any()))
            .thenReturn(List.of(thirdPartyLogin));
        when(sessions.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(authenticationProviders.findAll()).thenReturn(List.of(provider));
        when(clientMetadataService.location("127.0.0.1")).thenReturn("本机");

        DashboardStatisticsResponse response = service.statistics(DashboardRange.TODAY);

        assertThat(response.authenticationMethods()).containsExactly(new AuthenticationMethodMetricResponse("wechat", "微信认证", 1));
    }

    @Test
    void ranksApplicationsBySessionCountAndSplitsWeekIntoDailyBuckets() {
        Application portal = application("门户中心");
        Application console = application("管理后台");
        when(authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of());
        List<AuthenticationSession> rangeSessions = List.of(session(portal), session(portal), session(console));
        when(sessions.findByCreatedAtGreaterThanEqual(any())).thenReturn(rangeSessions);
        when(authenticationProviders.findAll()).thenReturn(List.of());

        DashboardStatisticsResponse response = service.statistics(DashboardRange.WEEK);

        assertThat(response.authenticationTrend()).hasSize(7);
        assertThat(response.applicationRanking()).containsExactly(
            new ApplicationRankingResponse(portal.getId(), "门户中心", 2),
            new ApplicationRankingResponse(console.getId(), "管理后台", 1));
        assertThat(response.authenticationMethods()).isEmpty();
        assertThat(response.loginLocations()).isEmpty();
    }

    @Test
    void splitsYearIntoMonthlyBuckets() {
        Instant march = ZonedDateTime.now().withMonth(3).withDayOfMonth(5).withHour(9).toInstant();
        AuthenticationEvent login = mock(AuthenticationEvent.class);
        when(login.getType()).thenReturn(AuthenticationEventType.LOGIN_SUCCESS);
        when(login.getCreatedAt()).thenReturn(march);
        when(login.getMethod()).thenReturn(AuthenticationMethods.PASSWORD);
        when(login.getIpAddress()).thenReturn("127.0.0.1");
        when(authenticationEvents.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(any())).thenReturn(List.of(login));
        when(sessions.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(authenticationProviders.findAll()).thenReturn(List.of());
        when(clientMetadataService.location("127.0.0.1")).thenReturn("本机");

        DashboardStatisticsResponse response = service.statistics(DashboardRange.YEAR);

        assertThat(response.authenticationTrend()).hasSize(12);
        assertThat(response.authenticationTrend().stream().filter(point -> point.count() > 0).toList())
            .containsExactly(new TrendPointResponse(march.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")), 1));
    }

    private AuthenticationEvent loginEvent(String method, String ipAddress, ZonedDateTime at) {
        AuthenticationEvent event = mock(AuthenticationEvent.class);
        when(event.getType()).thenReturn(AuthenticationEventType.LOGIN_SUCCESS);
        when(event.getCreatedAt()).thenReturn(at.toInstant());
        when(event.getMethod()).thenReturn(method);
        when(event.getIpAddress()).thenReturn(ipAddress);
        return event;
    }

    private Application application(String name) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(UUID.randomUUID());
        when(application.getName()).thenReturn(name);
        return application;
    }

    private AuthenticationSession session(Application application) {
        AuthenticationSession session = mock(AuthenticationSession.class);
        when(session.getApplication()).thenReturn(application);
        return session;
    }
}
