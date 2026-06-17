package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.ApplicationProtocol;
import com.antiam.dto.AccessDtos.ApplicationGroupResponse;
import com.antiam.dto.AccessDtos.ApplicationResponse;
import com.antiam.dto.AccessDtos.CreateApplicationGroupRequest;
import com.antiam.dto.AccessDtos.UpdateApplicationGroupRequest;
import com.antiam.service.AccessService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessControllerTest {

    private final AccessService access = mock(AccessService.class);
    private final AccessController controller = new AccessController(access);
    private final Principal principal = () -> "admin";

    @Test
    void exposesApplicationFieldsUsedByFrontend() {
        ApplicationResponse application = new ApplicationResponse(
            UUID.randomUUID(),
            "jwt",
            "JWT",
            ApplicationProtocol.JWT,
            "https://example.com/login",
            "JWT application",
            null,
            null,
            true,
            true);
        when(access.listApplications(null, true, "jwt")).thenReturn(List.of(application));

        List<ApplicationResponse> response = controller.applications(null, true, "jwt");

        assertThat(response).containsExactly(application);
        assertThat(response.get(0).description()).isEqualTo("JWT application");
        assertThat(response.get(0).selfServiceAccessRequestEnabled()).isTrue();
        verify(access).listApplications(null, true, "jwt");
    }

    @Test
    void deletesApplication() {
        UUID applicationId = UUID.randomUUID();

        controller.deleteApplication(applicationId, principal);

        verify(access).deleteApplication(applicationId, "admin");
    }

    @Test
    void exposesApplicationGroups() {
        ApplicationGroupResponse group = new ApplicationGroupResponse(
            UUID.randomUUID(),
            "standard",
            "标准应用",
            "标准协议应用",
            false,
            3,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-02T00:00:00Z"));
        when(access.listApplicationGroups("standard")).thenReturn(List.of(group));

        assertThat(controller.applicationGroups("standard")).containsExactly(group);

        verify(access).listApplicationGroups("standard");
    }

    @Test
    void createsApplicationGroup() {
        CreateApplicationGroupRequest request = new CreateApplicationGroupRequest("standard", "标准应用", "标准协议应用");
        ApplicationGroupResponse group = new ApplicationGroupResponse(UUID.randomUUID(), "standard", "标准应用", "标准协议应用", false, 0, null, null);
        when(access.createApplicationGroup(request, "admin")).thenReturn(group);

        assertThat(controller.createApplicationGroup(request, principal)).isEqualTo(group);

        verify(access).createApplicationGroup(request, "admin");
    }

    @Test
    void updatesApplicationGroup() {
        UUID groupId = UUID.randomUUID();
        UpdateApplicationGroupRequest request = new UpdateApplicationGroupRequest("业务应用", "核心业务应用");
        ApplicationGroupResponse group = new ApplicationGroupResponse(groupId, "business", "业务应用", "核心业务应用", false, 2, null, null);
        when(access.updateApplicationGroup(groupId, request, "admin")).thenReturn(group);

        assertThat(controller.updateApplicationGroup(groupId, request, principal)).isEqualTo(group);

        verify(access).updateApplicationGroup(groupId, request, "admin");
    }

    @Test
    void deletesApplicationGroup() {
        UUID groupId = UUID.randomUUID();

        controller.deleteApplicationGroup(groupId, principal);

        verify(access).deleteApplicationGroup(groupId, "admin");
    }
}
