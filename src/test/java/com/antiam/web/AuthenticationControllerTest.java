package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.ApplicationProtocol;
import com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;
import com.antiam.service.AuthenticationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationControllerTest {

    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final AuthenticationController controller = new AuthenticationController(authentication);

    @Test
    void exposesSessionFieldsUsedByPortalSessionPage() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthenticationSessionResponse session = new AuthenticationSessionResponse(
            sessionId,
            userId,
            null,
            ApplicationProtocol.OIDC,
            "sid-1",
            "127.0.0.1",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0 Safari/537.36",
            null,
            "PC",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:05:00Z"),
            Instant.parse("2026-01-01T01:00:00Z"),
            null,
            true);
        when(authentication.listSessions(userId, null, true)).thenReturn(List.of(session));

        List<AuthenticationSessionResponse> response = controller.sessions(userId, null, true);

        assertThat(response).containsExactly(session);
        assertThat(response.get(0).createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.get(0).lastAccessedAt()).isEqualTo(Instant.parse("2026-01-01T00:05:00Z"));
        assertThat(response.get(0).deviceType()).isEqualTo("PC");
        verify(authentication).listSessions(userId, null, true);
    }
}
