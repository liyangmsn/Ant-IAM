package com.antiam.service.thirdparty;

import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.raw;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.required;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.text;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.textOrDefault;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.uri;

import com.antiam.domain.AuthenticationProviderKind;
import com.antiam.service.wechatwork.WechatWorkClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 企业微信登录：默认使用网页扫码登录（wwlogin），也支持企业微信客户端内的 OAuth2 网页授权。
 */
@Component
@RequiredArgsConstructor
public class WechatWorkLoginAdapter implements ThirdPartyAuthAdapter {

    static final String QRCODE_AUTHORIZE_ENDPOINT = "https://login.work.weixin.qq.com/wwlogin/sso/login";
    static final String OAUTH_AUTHORIZE_ENDPOINT = "https://open.weixin.qq.com/connect/oauth2/authorize";

    private final WechatWorkClient client;

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.WECHAT_WORK;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        String corpId = corpId(configuration);
        String agentId = required(configuration, "agentId");
        if (isOauthMode(configuration)) {
            return uri(textOrDefault(configuration, "authorizeEndpoint", OAUTH_AUTHORIZE_ENDPOINT))
                .queryParam("appid", corpId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", textOrDefault(configuration, "scope", "snsapi_base"))
                .queryParam("state", state)
                .queryParam("agentid", agentId)
                .fragment("wechat_redirect")
                .encode()
                .build()
                .toUriString();
        }
        return uri(textOrDefault(configuration, "authorizeEndpoint", QRCODE_AUTHORIZE_ENDPOINT))
            .queryParam("login_type", "CorpApp")
            .queryParam("appid", corpId)
            .queryParam("agentid", agentId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("state", state)
            .encode()
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        String endpoint = textOrDefault(configuration, "endpoint", WechatWorkClient.DEFAULT_ENDPOINT);
        String corpId = corpId(configuration);
        String secret = required(configuration, "appSecret");

        JsonNode userInfo = client.get(endpoint, corpId, secret, "/cgi-bin/auth/getuserinfo", Map.of("code", code));
        assertSuccess(userInfo, "WeCom userinfo");
        String userId = text(userInfo, "userid");
        String openId = text(userInfo, "openid");
        if (userId == null) {
            // 非企业成员只会返回 openid，不能作为企业身份登录。
            throw new IllegalStateException("WeCom login rejected: user is not a member of the enterprise"
                + (openId == null ? "" : " (openid=" + openId + ")"));
        }

        JsonNode member = client.get(endpoint, corpId, secret, "/cgi-bin/user/get", Map.of("userid", userId));
        if (!WechatWorkClient.isSuccess(member)) {
            member = null;
        }
        String userTicket = text(userInfo, "user_ticket");
        JsonNode detail = userTicket == null ? null : userDetail(endpoint, corpId, secret, userTicket);

        return new ThirdPartyProfile(
            userId,
            null,
            textOrDefault(member, "name", userId),
            firstText(detail, member, "email", "biz_mail"),
            firstText(detail, member, "mobile"),
            firstText(detail, member, "avatar"),
            raw(
                "corpId", corpId,
                "userid", userId,
                "raw", userInfo,
                "member", member,
                "detail", detail));
    }

    private JsonNode userDetail(String endpoint, String corpId, String secret, String userTicket) {
        JsonNode response = client.post(endpoint, corpId, secret, "/cgi-bin/auth/getuserdetail", Map.of("user_ticket", userTicket));
        return WechatWorkClient.isSuccess(response) ? response : null;
    }

    private boolean isOauthMode(JsonNode configuration) {
        return "oauth".equalsIgnoreCase(textOrDefault(configuration, "loginMode", "qrcode"));
    }

    private String corpId(JsonNode configuration) {
        String corpId = text(configuration, "corpId");
        return corpId == null ? required(configuration, "appId") : corpId;
    }

    private String firstText(JsonNode primary, JsonNode fallback, String... fields) {
        for (JsonNode node : new JsonNode[] {primary, fallback}) {
            for (String field : fields) {
                String value = text(node, field);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private void assertSuccess(JsonNode response, String operation) {
        if (!WechatWorkClient.isSuccess(response)) {
            throw new IllegalStateException(operation + " request failed: " + WechatWorkClient.errorMessage(response));
        }
    }
}
