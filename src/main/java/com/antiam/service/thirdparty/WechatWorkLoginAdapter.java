package com.antiam.service.thirdparty;

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
public class WechatWorkLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";
    private static final String DEFAULT_TOKEN_ENDPOINT = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String DEFAULT_USERINFO_ENDPOINT = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String DEFAULT_USER_DETAIL_ENDPOINT = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserdetail";

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.WECHAT_WORK;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("appid", required(configuration, "appId"))
            .queryParam("agentid", required(configuration, "agentId"))
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        JsonNode token = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "tokenEndpoint", DEFAULT_TOKEN_ENDPOINT))
                .queryParam("corpid", required(configuration, "appId"))
                .queryParam("corpsecret", required(configuration, "appSecret"))
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(token, "WeCom token");

        JsonNode userInfo = RestClient.create()
            .get()
            .uri(uri(textOrDefault(configuration, "userInfoEndpoint", DEFAULT_USERINFO_ENDPOINT))
                .queryParam("access_token", required(token, "access_token"))
                .queryParam("code", code)
                .build()
                .toUriString())
            .retrieve()
            .body(JsonNode.class);
        assertNoError(userInfo, "WeCom userinfo");

        JsonNode detail = userDetail(configuration, required(token, "access_token"), text(userInfo, "user_ticket"));
        String userId = text(userInfo, "userid");
        String openId = text(userInfo, "openid");
        String subject = userId == null ? openId : userId;
        if (subject == null) {
            throw new IllegalStateException("WeCom userinfo request failed: neither userid nor openid returned");
        }

        return new ThirdPartyProfile(
            subject,
            text(userInfo, "external_userid"),
            textOrDefault(detail, "name", subject),
            text(detail, "email"),
            text(detail, "mobile"),
            text(detail, "avatar"),
            raw(
                "userid", userId,
                "openid", openId,
                "external_userid", text(userInfo, "external_userid"),
                "user_ticket", text(userInfo, "user_ticket"),
                "raw", userInfo,
                "detail", detail));
    }

    private JsonNode userDetail(JsonNode configuration, String accessToken, String userTicket) {
        if (userTicket == null) {
            return null;
        }
        JsonNode response = RestClient.create()
            .post()
            .uri(uri(textOrDefault(configuration, "userDetailEndpoint", DEFAULT_USER_DETAIL_ENDPOINT))
                .queryParam("access_token", accessToken)
                .build()
                .toUriString())
            .body(raw("user_ticket", userTicket))
            .retrieve()
            .body(JsonNode.class);
        assertNoError(response, "WeCom user detail");
        return response;
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
