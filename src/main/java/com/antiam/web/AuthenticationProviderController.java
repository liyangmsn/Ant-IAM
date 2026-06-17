package com.antiam.web;

import static com.antiam.dto.AuthenticationProviderDtos.AuthenticationProviderResponse;
import static com.antiam.dto.AuthenticationProviderDtos.CreateAuthenticationProviderRequest;
import static com.antiam.dto.AuthenticationProviderDtos.UpdateAuthenticationProviderRequest;

import com.antiam.domain.AuthenticationProviderKind;
import com.antiam.domain.AuthenticationProviderType;
import com.antiam.service.AuthenticationProviderService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authentication-providers")
@RequiredArgsConstructor
@Tag(name = "身份提供商", description = "社交认证源和企业认证源配置管理接口")
public class AuthenticationProviderController {

    private final AuthenticationProviderService providers;

    @Operation(summary = "查询身份提供商", description = "查询微信、GitHub、钉钉、飞书等认证源配置。")
    @GetMapping
    List<AuthenticationProviderResponse> list(
        @Parameter(description = "认证源类型") @RequestParam(required = false) AuthenticationProviderType type,
        @Parameter(description = "认证源提供商") @RequestParam(required = false) AuthenticationProviderKind provider,
        @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled,
        @Parameter(description = "关键字，匹配编码、名称、类型或说明") @RequestParam(required = false) String keyword
    ) {
        return providers.list(type, provider, enabled, keyword);
    }

    @Operation(summary = "创建身份提供商", description = "创建新的第三方认证源配置。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AuthenticationProviderResponse create(
        @Parameter(description = "身份提供商创建请求") @Valid @RequestBody CreateAuthenticationProviderRequest request,
        Principal principal
    ) {
        return providers.create(request, principal.getName());
    }

    @Operation(summary = "获取身份提供商", description = "根据 UUID 返回认证源详情。")
    @GetMapping("/{providerId}")
    AuthenticationProviderResponse get(@Parameter(description = "身份提供商 UUID") @PathVariable UUID providerId) {
        return providers.get(providerId);
    }

    @Operation(summary = "更新身份提供商", description = "更新认证源名称、说明、展示状态和连接配置。")
    @PutMapping("/{providerId}")
    AuthenticationProviderResponse update(
        @Parameter(description = "身份提供商 UUID") @PathVariable UUID providerId,
        @Parameter(description = "身份提供商更新请求") @Valid @RequestBody UpdateAuthenticationProviderRequest request,
        Principal principal
    ) {
        return providers.update(providerId, request, principal.getName());
    }

    @Operation(summary = "启用身份提供商", description = "启用认证源，使其可以出现在登录流程中。")
    @PostMapping("/{providerId}/enable")
    AuthenticationProviderResponse enable(@Parameter(description = "身份提供商 UUID") @PathVariable UUID providerId, Principal principal) {
        return providers.enable(providerId, principal.getName());
    }

    @Operation(summary = "停用身份提供商", description = "停用认证源。")
    @PostMapping("/{providerId}/disable")
    AuthenticationProviderResponse disable(@Parameter(description = "身份提供商 UUID") @PathVariable UUID providerId, Principal principal) {
        return providers.disable(providerId, principal.getName());
    }

    @Operation(summary = "删除身份提供商", description = "删除认证源配置。")
    @DeleteMapping("/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(description = "身份提供商 UUID") @PathVariable UUID providerId, Principal principal) {
        providers.delete(providerId, principal.getName());
    }
}
