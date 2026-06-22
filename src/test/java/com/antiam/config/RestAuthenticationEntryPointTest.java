package com.antiam.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class RestAuthenticationEntryPointTest {

    @Test
    void returnsUnauthorizedForUnauthenticatedApiRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint().commence(
            new MockHttpServletRequest("GET", "/api/v1/dashboard/summary"),
            response,
            new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
