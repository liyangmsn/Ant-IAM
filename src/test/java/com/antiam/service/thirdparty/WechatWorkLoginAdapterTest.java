package com.antiam.service.thirdparty;

import static org.assertj.core.api.Assertions.assertThat;

import com.antiam.service.wechatwork.WechatWorkClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WechatWorkLoginAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WechatWorkLoginAdapter adapter = new WechatWorkLoginAdapter(new WechatWorkClient(objectMapper));

    @Test
    void buildsWebQrCodeLoginUrlByDefault() throws Exception {
        String url = adapter.authorizationUrl(
            objectMapper.readTree("{\"appId\":\"ww123\",\"agentId\":\"1000002\"}"),
            "https://iam.example.com/login/callback/wechat-work",
            "v1.abc.def");

        assertThat(url).isEqualTo("https://login.work.weixin.qq.com/wwlogin/sso/login?login_type=CorpApp&appid=ww123&agentid=1000002"
            + "&redirect_uri=https://iam.example.com/login/callback/wechat-work&state=v1.abc.def");
    }

    @Test
    void buildsInAppOauthUrlWhenConfigured() throws Exception {
        String url = adapter.authorizationUrl(
            objectMapper.readTree("{\"corpId\":\"ww123\",\"agentId\":\"1000002\",\"loginMode\":\"oauth\"}"),
            "https://iam.example.com/cb",
            "s");

        assertThat(url).startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?appid=ww123&redirect_uri=https://iam.example.com/cb")
            .contains("scope=snsapi_base", "agentid=1000002")
            .endsWith("#wechat_redirect");
    }
}
