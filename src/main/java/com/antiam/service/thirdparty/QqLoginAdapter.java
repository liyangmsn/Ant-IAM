package com.antiam.service.thirdparty;

import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.redirectUri;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.raw;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.required;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.text;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.textOrDefault;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.uri;

import com.antiam.domain.AuthenticationProviderKind;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class QqLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://graph.qq.com/oauth2.0/authorize";
    private static final String DEFAULT_TOKEN_ENDPOINT = "https://graph.qq.com/oauth2.0/token";
    private static final String DEFAULT_OPENID_ENDPOINT = "https://graph.qq.com/oauth2.0/me";
    private static final String DEFAULT_USERINFO_ENDPOINT = "https://graph.qq.com/user/get_user_info";

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.QQ;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("response_type", "code")
            .queryParam("client_id", required(configuration, "appId"))
            .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
            .queryParam("scope", textOrDefault(configuration, "scope", "get_user_info"))
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        String appId = required(configuration, "appId");
        JsonNode token = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "tokenEndpoint", DEFAULT_TOKEN_ENDPOINT))
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", appId)
                .queryParam("client_secret", required(configuration, "appSecret"))
                .queryParam("code", code)
                .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
                .queryParam("fmt", "json")
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(token, "QQ token");
        String accessToken = required(token, "access_token");
        JsonNode openid = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "openidEndpoint", DEFAULT_OPENID_ENDPOINT))
                .queryParam("access_token", accessToken)
                .queryParam("fmt", "json")
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(openid, "QQ openid");
        String subject = required(openid, "openid");
        JsonNode user = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "userInfoEndpoint", DEFAULT_USERINFO_ENDPOINT))
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", appId)
                .queryParam("openid", subject)
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(user, "QQ userinfo");
        return new ThirdPartyProfile(
            subject,
            text(openid, "unionid"),
            textOrDefault(user, "nickname", subject),
            null,
            null,
            textOrDefault(user, "figureurl_qq_2", text(user, "figureurl_qq_1")),
            raw(
                "openid", subject,
                "nickname", text(user, "nickname"),
                "raw", user));
    }

    private void assertNoError(JsonNode response, String operation) {
        if (response == null) {
            throw new IllegalStateException(operation + " request failed: empty response");
        }
        if (response.has("error")) {
            throw new IllegalStateException(operation + " request failed: " + response.path("error_description").asText(response.path("error").asText()));
        }
        if (response.has("ret") && response.path("ret").asInt(0) != 0) {
            throw new IllegalStateException(operation + " request failed: " + response.path("msg").asText("unknown error"));
        }
    }
}
