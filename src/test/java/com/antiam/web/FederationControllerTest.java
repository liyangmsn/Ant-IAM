package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.dto.FederationDtos.JwtSsoTokenResponse;
import com.antiam.dto.FederationDtos.JwtSsoVerificationResponse;
import com.antiam.dto.FederationDtos.VerifyJwtTokenRequest;
import com.antiam.service.FederationService;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class FederationControllerTest {

    private final FederationService federation = mock(FederationService.class);
    private final FederationController controller = new FederationController(federation);
    private final Principal principal = () -> "alice";

    @Test
    void issuesJwtSsoTokenForCurrentUser() {
        Instant issuedAt = Instant.now();
        JwtSsoTokenResponse expected = new JwtSsoTokenResponse(
            "signed-token",
            "Bearer",
            "http://localhost",
            "checkout-app",
            "alice",
            issuedAt,
            issuedAt.plusSeconds(600));
        when(federation.issueJwtSsoToken("checkout-app", "alice", "http://localhost")).thenReturn(expected);

        JwtSsoTokenResponse response = controller.jwtSso(
            "checkout-app",
            new MockHttpServletRequest("GET", "/jwt/sso"),
            principal);

        assertThat(response).isEqualTo(expected);
        verify(federation).issueJwtSsoToken("checkout-app", "alice", "http://localhost");
    }

    @Test
    void verifiesJwtToken() {
        JwtSsoVerificationResponse expected = new JwtSsoVerificationResponse(
            true,
            "kid-1",
            "http://localhost",
            "checkout-app",
            "alice",
            Instant.now(),
            Instant.now().plusSeconds(600),
            Map.of("email", "alice@example.com"),
            null,
            null);
        when(federation.verifyJwtSsoToken("signed-token")).thenReturn(expected);

        JwtSsoVerificationResponse response = controller.verifyJwtToken(new VerifyJwtTokenRequest("signed-token"));

        assertThat(response).isEqualTo(expected);
        verify(federation).verifyJwtSsoToken("signed-token");
    }
}
