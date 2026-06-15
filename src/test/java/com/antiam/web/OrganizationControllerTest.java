package com.antiam.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.antiam.service.OrganizationService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationControllerTest {

    private final OrganizationService organizations = mock(OrganizationService.class);
    private final OrganizationController controller = new OrganizationController(organizations);
    private final Principal principal = () -> "admin";

    @Test
    void deletesOrganization() {
        UUID organizationId = UUID.randomUUID();

        controller.delete(organizationId, principal);

        verify(organizations).delete(organizationId, "admin");
    }
}
