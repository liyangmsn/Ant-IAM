package com.antiam.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.antiam.service.AuthenticationProviderService;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationProviderControllerTest {

    private final AuthenticationProviderService providers = mock(AuthenticationProviderService.class);
    private final AuthenticationProviderController controller = new AuthenticationProviderController(providers);
    private final Principal principal = () -> "admin";

    @Test
    void deletesAuthenticationProvider() {
        UUID providerId = UUID.randomUUID();

        controller.delete(providerId, principal);

        verify(providers).delete(providerId, "admin");
    }
}
