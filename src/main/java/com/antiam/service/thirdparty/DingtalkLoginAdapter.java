package com.antiam.service.thirdparty;

import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.redirectUri;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.raw;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.required;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.textOrDefault;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.uri;

import com.antiam.domain.AuthenticationProviderKind;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiSnsGetuserinfoBycodeRequest;
import com.dingtalk.api.response.OapiSnsGetuserinfoBycodeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.taobao.api.ApiException;
import org.springframework.stereotype.Component;

@Component
public class DingtalkLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://oapi.dingtalk.com/connect/qrconnect";
    private static final String DEFAULT_ENDPOINT = "https://oapi.dingtalk.com";

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.DINGTALK;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("appid", required(configuration, "appId"))
            .queryParam("response_type", "code")
            .queryParam("scope", textOrDefault(configuration, "scope", "snsapi_login"))
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        try {
            OapiSnsGetuserinfoBycodeRequest request = new OapiSnsGetuserinfoBycodeRequest();
            request.setTmpAuthCode(code);
            OapiSnsGetuserinfoBycodeResponse response = new DefaultDingTalkClient(endpoint(configuration) + "/sns/getuserinfo_bycode")
                .execute(request, required(configuration, "appId"), required(configuration, "appSecret"));
            if (response == null || !response.isSuccess() || response.getUserInfo() == null) {
                String message = response == null ? "empty response" : defaultString(response.getErrmsg(), "unknown error");
                throw new IllegalStateException("DingTalk login request failed: " + message);
            }
            OapiSnsGetuserinfoBycodeResponse.UserInfo user = response.getUserInfo();
            return new ThirdPartyProfile(
                requireValue(user.getOpenid(), "openid"),
                user.getUnionid(),
                defaultString(user.getNick(), user.getOpenid()),
                null,
                null,
                null,
                raw(
                    "openid", user.getOpenid(),
                    "unionid", user.getUnionid(),
                    "nick", user.getNick()));
        } catch (ApiException ex) {
            throw new IllegalStateException("DingTalk login request failed: " + ex.getMessage(), ex);
        }
    }

    private String endpoint(JsonNode configuration) {
        return textOrDefault(configuration, "endpoint", DEFAULT_ENDPOINT);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String requireValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DingTalk login field is required: " + field);
        }
        return value;
    }
}
