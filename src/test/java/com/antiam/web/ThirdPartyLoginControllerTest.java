package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.dto.AuthenticationDtos.ThirdPartyAuthorizeResponse;
import com.antiam.service.ThirdPartyLoginService;
import org.junit.jupiter.api.Test;

class ThirdPartyLoginControllerTest {

    private final ThirdPartyLoginService logins = mock(ThirdPartyLoginService.class);
    private final ThirdPartyLoginController controller = new ThirdPartyLoginController(logins);

    @Test
    void exposesAuthorizeUrl() {
        ThirdPartyAuthorizeResponse expected = new ThirdPartyAuthorizeResponse("wechat", "https://example.com/auth", "state");
        when(logins.authorize("wechat", "https://iam.example.com/callback", "state")).thenReturn(expected);

        ThirdPartyAuthorizeResponse response = controller.authorize("wechat", "https://iam.example.com/callback", "state");

        assertThat(response).isEqualTo(expected);
        verify(logins).authorize("wechat", "https://iam.example.com/callback", "state");
    }
}
