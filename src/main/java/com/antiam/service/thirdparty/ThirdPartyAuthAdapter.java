package com.antiam.service.thirdparty;

import com.antiam.domain.AuthenticationProviderKind;
import com.fasterxml.jackson.databind.JsonNode;

public interface ThirdPartyAuthAdapter {

    boolean supports(AuthenticationProviderKind provider);

    String authorizationUrl(JsonNode configuration, String redirectUri, String state);

    ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri);
}
