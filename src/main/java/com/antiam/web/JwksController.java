package com.antiam.web;

import static com.antiam.dto.JwkDtos.JwksResponse;
import static com.antiam.dto.JwkDtos.SigningKeyResponse;

import com.antiam.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "JWT 签名密钥", description = "OIDC JWKS 和 JWT 签名密钥轮换管理接口")
public class JwksController {

    private final JwtService jwtService;

    /**
     * 输出 OIDC JWKS 公钥集。
     */
    @Operation(summary = "获取 JWKS", description = "返回 OIDC/OAuth 客户端验证 JWT 所需的 JSON Web Key Set。")
    @GetMapping("/oauth2/jwks")
    JwksResponse jwks() {
        return jwtService.jwks();
    }

    /**
     * 查询 JWT 签名密钥列表。
     */
    @Operation(summary = "查询 JWT 签名密钥", description = "查询服务端签名密钥，支持启用状态和关键字过滤。")
    @GetMapping("/api/v1/jwt-signing-keys")
    List<SigningKeyResponse> signingKeys(
        @Parameter(description = "是否活跃") @RequestParam(required = false) Boolean active,
        @Parameter(description = "关键字，匹配 keyId 或算法") @RequestParam(required = false) String keyword
    ) {
        return jwtService.listKeys(active, keyword);
    }

    /**
     * 查询 JWT 签名密钥详情。
     */
    @Operation(summary = "获取 JWT 签名密钥详情", description = "根据签名密钥 UUID 返回密钥元数据。")
    @GetMapping("/api/v1/jwt-signing-keys/{keyId}")
    SigningKeyResponse signingKey(@Parameter(description = "签名密钥 UUID") @PathVariable UUID keyId) {
        return jwtService.getKey(keyId);
    }

    /**
     * 轮换 JWT 签名密钥。
     */
    @Operation(summary = "轮换 JWT 签名密钥", description = "创建新的活跃签名密钥，用于后续 JWT 签发。")
    @PostMapping("/api/v1/jwt-signing-keys/rotations")
    @ResponseStatus(HttpStatus.CREATED)
    SigningKeyResponse rotateSigningKey(Principal principal) {
        return jwtService.rotateKey(principal.getName());
    }

    /**
     * 退役 JWT 签名密钥。
     */
    @Operation(summary = "退役 JWT 签名密钥", description = "退役非唯一活跃签名密钥；服务端会保护最后一把活跃密钥不被退役。")
    @PostMapping("/api/v1/jwt-signing-keys/{keyId}/retire")
    SigningKeyResponse retireSigningKey(@Parameter(description = "签名密钥 UUID") @PathVariable UUID keyId, Principal principal) {
        return jwtService.retireKey(keyId, principal.getName());
    }
}
