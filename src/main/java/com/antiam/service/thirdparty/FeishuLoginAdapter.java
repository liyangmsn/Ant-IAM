package com.antiam.service.thirdparty;

import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.redirectUri;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.raw;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.required;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.text;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.textOrDefault;
import static com.antiam.service.thirdparty.ThirdPartyAuthSupport.uri;

import com.antiam.domain.AuthenticationProviderKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.lark.oapi.Client;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenResp;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenRespBody;
import org.springframework.stereotype.Component;

@Component
public class FeishuLoginAdapter implements ThirdPartyAuthAdapter {

    private static final String DEFAULT_AUTHORIZE_ENDPOINT = "https://accounts.feishu.cn/open-apis/authen/v1/index";

    @Override
    public boolean supports(AuthenticationProviderKind provider) {
        return provider == AuthenticationProviderKind.FEISHU;
    }

    @Override
    public String authorizationUrl(JsonNode configuration, String redirectUri, String state) {
        return uri(textOrDefault(configuration, "authorizeEndpoint", DEFAULT_AUTHORIZE_ENDPOINT))
            .queryParam("app_id", required(configuration, "appId"))
            .queryParam("redirect_uri", redirectUri(configuration, redirectUri))
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    @Override
    public ThirdPartyProfile exchange(JsonNode configuration, String code, String redirectUri) {
        try {
            CreateAccessTokenReq request = new CreateAccessTokenReq();
            CreateAccessTokenReqBody body = new CreateAccessTokenReqBody();
            body.setGrantType("authorization_code");
            body.setCode(code);
            request.setCreateAccessTokenReqBody(body);
            CreateAccessTokenResp response = client(configuration).authen().v1().accessToken().create(request);
            if (response == null || !response.success() || response.getData() == null) {
                String message = response == null ? "empty response" : response.getMsg();
                throw new IllegalStateException("Feishu login request failed: " + message);
            }
            CreateAccessTokenRespBody data = response.getData();
            String subject = firstNonBlank(data.getUserId(), data.getOpenId(), data.getUnionId());
            if (subject == null) {
                throw new IllegalStateException("Feishu login response does not contain user id");
            }
            return new ThirdPartyProfile(
                subject,
                data.getUnionId(),
                firstNonBlank(data.getName(), data.getEnName(), subject),
                firstNonBlank(data.getEmail(), data.getEnterpriseEmail()),
                data.getMobile(),
                firstNonBlank(data.getAvatarUrl(), data.getAvatarBig(), data.getAvatarMiddle(), data.getAvatarThumb()),
                raw(
                    "userId", data.getUserId(),
                    "openId", data.getOpenId(),
                    "unionId", data.getUnionId(),
                    "tenantKey", data.getTenantKey()));
        } catch (Exception ex) {
            throw new IllegalStateException("Feishu login request failed: " + ex.getMessage(), ex);
        }
    }

    private Client client(JsonNode configuration) {
        Client.Builder builder = Client.newBuilder(required(configuration, "appId"), required(configuration, "appSecret"));
        String endpoint = text(configuration, "endpoint");
        if (endpoint != null) {
            builder.openBaseUrl(endpoint);
        }
        return builder.build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
