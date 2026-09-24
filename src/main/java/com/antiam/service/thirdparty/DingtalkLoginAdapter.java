package com.antiam.service.thirdparty;

import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.redirectUri;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.raw;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.required;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.text;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.textOrDefault;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.uri;

import com.antiam.domain.AuthenticationProviderKind;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class DingtalkLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://login.dingtalk.com/oauth2/auth";
    private static final String DEFAULT_TOKEN_ENDPOINT = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String DEFAULT_USERINFO_ENDPOINT = "https://api.dingtalk.com/v1.0/contact/users/me";

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.DINGTALK;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("client_id", required(configuration, "appId"))
            .queryParam("response_type", "code")
            .queryParam("scope", textOrDefault(configuration, "scope", "openid"))
            .queryParam("state", state)
            .queryParam("prompt", textOrDefault(configuration, "prompt", "consent"))
            .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        String appId = required(configuration, "appId");
        String tokenResponse = RestClient.create()
            .post()
            .uri(textOrDefault(configuration, "tokenEndpoint", DEFAULT_TOKEN_ENDPOINT))
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "clientId", appId,
                "clientSecret", required(configuration, "appSecret"),
                "code", code,
                "grantType", "authorization_code"))
            .retrieve()
            .body(String.class);
        JsonNode token = parse(tokenResponse, "DingTalk token");
        assertNoError(token, "DingTalk token");
        String accessToken = required(token, "accessToken");
        String userResponse = RestClient.create()
            .get()
            .uri(textOrDefault(configuration, "userInfoEndpoint", DEFAULT_USERINFO_ENDPOINT))
            .header("x-acs-dingtalk-access-token", accessToken)
            .retrieve()
            .body(String.class);
        JsonNode user = parse(userResponse, "DingTalk userinfo");
        assertNoError(user, "DingTalk userinfo");
        String subject = required(user, "openId");
        return new ThirdPartyProfile(
            subject,
            text(user, "unionId"),
            textOrDefault(user, "nick", subject),
            text(user, "email"),
            text(user, "mobile"),
            text(user, "avatarUrl"),
            raw(
                "openId", subject,
                "unionId", text(user, "unionId"),
                "raw", user));
    }

    private void assertNoError(JsonNode response, String operation) {
        if (response == null) {
            throw new IllegalStateException(operation + " request failed: empty response");
        }
        if (response.has("code") && !response.path("code").asText().isBlank()) {
            throw new IllegalStateException(operation + " request failed: " + response.path("message").asText(response.path("code").asText()));
        }
    }

    private JsonNode parse(String response, String operation) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException(operation + " request failed: empty response");
        }
        try {
            return objectMapper.readTree(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(operation + " request failed: invalid JSON response", ex);
        }
    }
}
