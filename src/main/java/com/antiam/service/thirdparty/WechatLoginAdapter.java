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
public class WechatLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://open.weixin.qq.com/connect/qrconnect";
    private static final String DEFAULT_TOKEN_ENDPOINT = "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String DEFAULT_USERINFO_ENDPOINT = "https://api.weixin.qq.com/sns/userinfo";

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.WECHAT;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("appid", required(configuration, "appId"))
            .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
            .queryParam("response_type", "code")
            .queryParam("scope", textOrDefault(configuration, "scope", "snsapi_login"))
            .queryParam("state", state)
            .fragment("wechat_redirect")
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        JsonNode token = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "tokenEndpoint", DEFAULT_TOKEN_ENDPOINT))
                .queryParam("appid", required(configuration, "appId"))
                .queryParam("secret", required(configuration, "appSecret"))
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(token, "WeChat token");
        String openId = required(token, "openid");
        JsonNode user = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "userInfoEndpoint", DEFAULT_USERINFO_ENDPOINT))
                .queryParam("access_token", required(token, "access_token"))
                .queryParam("openid", openId)
                .queryParam("lang", textOrDefault(configuration, "lang", "zh_CN"))
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(user, "WeChat userinfo");
        return new ThirdPartyProfile(
            openId,
            text(user, "unionid"),
            textOrDefault(user, "nickname", openId),
            null,
            null,
            text(user, "headimgurl"),
            raw(
                "openid", openId,
                "unionid", text(user, "unionid"),
                "nickname", text(user, "nickname"),
                "raw", user));
    }

    private void assertNoError(JsonNode response, String operation) {
        if (response == null) {
            throw new IllegalStateException(operation + " request failed: empty response");
        }
        if (response.has("errcode") && response.path("errcode").asInt(0) != 0) {
            throw new IllegalStateException(operation + " request failed: " + response.path("errmsg").asText("unknown error"));
        }
    }
}
