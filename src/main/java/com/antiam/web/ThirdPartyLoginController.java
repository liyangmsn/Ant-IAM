package com.antiam.web;

import static com.antiam.dto.AuthenticationDtos.ThirdPartyAuthorizeResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyLoginCallbackRequest;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyLoginResponse;

import com.antiam.service.ThirdPartyLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authentication/third-party")
@RequiredArgsConstructor
@Tag(name = "第三方登录", description = "微信、QQ、飞书、钉钉等第三方登录发起和回调接口")
public class ThirdPartyLoginController {

    private final ThirdPartyLoginService logins;

    @Operation(summary = "生成第三方授权地址", description = "根据认证源配置生成 OAuth/扫码登录授权地址。")
    @GetMapping("/{providerKey}/authorize")
    ThirdPartyAuthorizeResponse authorize(
        @Parameter(description = "认证源编码") @PathVariable String providerKey,
        @Parameter(description = "回调地址；为空则使用认证源配置") @RequestParam(required = false) String redirectUri,
        @Parameter(description = "前端透传 state；为空则后端生成") @RequestParam(required = false) String state
    ) {
        return logins.authorize(providerKey, redirectUri, state);
    }

    @Operation(summary = "处理第三方登录回调", description = "使用授权码换取第三方用户信息，自动创建或匹配本地用户并创建登录会话。")
    @PostMapping("/{providerKey}/callback")
    @ResponseStatus(HttpStatus.CREATED)
    ThirdPartyLoginResponse callback(
        @Parameter(description = "认证源编码") @PathVariable String providerKey,
        @Parameter(description = "第三方登录回调请求") @Valid @RequestBody ThirdPartyLoginCallbackRequest request,
        HttpServletRequest httpRequest
    ) {
        return logins.callback(providerKey, request, clientIp(httpRequest), httpRequest.getHeader("User-Agent"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
