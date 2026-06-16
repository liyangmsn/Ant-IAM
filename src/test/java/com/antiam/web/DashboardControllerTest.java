package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.dto.DashboardDtos.DashboardSummaryResponse;
import com.antiam.service.DashboardService;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {

    private final DashboardService dashboard = mock(DashboardService.class);
    private final DashboardController controller = new DashboardController(dashboard);

    @Test
    void exposesConsoleSummaryFieldsUsedByFrontend() {
        DashboardSummaryResponse summary = new DashboardSummaryResponse(
            12,
            10,
            3,
            5,
            2,
            4,
            9,
            1,
            0,
            7);
        when(dashboard.summary()).thenReturn(summary);

        DashboardSummaryResponse response = controller.summary();

        assertThat(response.users()).isEqualTo(12);
        assertThat(response.activeUsers()).isEqualTo(10);
        assertThat(response.organizations()).isEqualTo(3);
        assertThat(response.applications()).isEqualTo(5);
        assertThat(response.identitySources()).isEqualTo(2);
        assertThat(response.activeSessions()).isEqualTo(4);
        assertThat(response.pendingAccessRequests()).isEqualTo(7);
    }
}
