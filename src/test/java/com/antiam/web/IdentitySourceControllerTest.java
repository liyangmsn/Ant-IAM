package com.antiam.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.antiam.service.IdentitySourceService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentitySourceControllerTest {

    private final IdentitySourceService identitySources = mock(IdentitySourceService.class);
    private final IdentitySourceController controller = new IdentitySourceController(identitySources);
    private final Principal principal = () -> "admin";

    @Test
    void deletesIdentitySource() {
        UUID identitySourceId = UUID.randomUUID();

        controller.delete(identitySourceId, principal);

        verify(identitySources).delete(identitySourceId, "admin");
    }
}
