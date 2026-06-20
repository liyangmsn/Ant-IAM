package com.antiam.web;

import static com.antiam.dto.AuthenticationDtos.ThirdPartyAuthorizeResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyBindingResponse;
import static com.antiam.dto.AuthenticationDtos.ThirdPartyLoginCallbackRequest;

import com.antiam.service.ThirdPartyLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/third-party-bindings")
@RequiredArgsConstructor
@Tag(name = "第三方账号绑定", description = "用户账号与微信、QQ、飞书、钉钉等第三方身份的绑定关系")
public class UserThirdPartyBindingController {

    private final ThirdPartyLoginService logins;

    @Operation(summary = "查询用户第三方绑定", description = "返回指定用户已绑定的微信、QQ、飞书、钉钉等第三方身份。")
    @GetMapping
    List<ThirdPartyBindingResponse> bindings(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return logins.bindings(userId);
    }

    @Operation(summary = "生成第三方绑定授权地址", description = "当前用户从账号中心发起第三方身份绑定。")
    @PostMapping("/{providerKey}/authorize")
    ThirdPartyAuthorizeResponse authorizeBinding(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "认证源编码") @PathVariable String providerKey,
        @Parameter(description = "绑定回调地址") @RequestParam(required = false) String redirectUri,
        @Parameter(description = "前端透传 state") @RequestParam(required = false) String state
    ) {
        return logins.authorizeBinding(userId, providerKey, redirectUri, state);
    }

    @Operation(summary = "处理第三方绑定回调", description = "使用授权码换取第三方用户信息，并绑定到当前本地用户。")
    @PostMapping("/{providerKey}/callback")
    @ResponseStatus(HttpStatus.CREATED)
    ThirdPartyBindingResponse bind(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "认证源编码") @PathVariable String providerKey,
        @Parameter(description = "第三方绑定回调请求") @Valid @RequestBody ThirdPartyLoginCallbackRequest request,
        Principal principal
    ) {
        return logins.bind(userId, providerKey, request, principal.getName());
    }

    @Operation(summary = "解除第三方绑定", description = "删除指定用户与第三方身份的绑定关系。")
    @DeleteMapping("/{bindingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unbind(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "绑定 UUID") @PathVariable UUID bindingId,
        Principal principal
    ) {
        logins.unbind(userId, bindingId, principal.getName());
    }
}
