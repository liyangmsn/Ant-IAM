package com.antiam.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "安全设置", description = "控制台通用安全和密码策略页面设置接口")
public class SecurityHeaderController {

    /**
     * 供 nginx auth_request 读取当前 CSP，响应头由 ContentSecurityPolicyFilter 写入。
     */
    @Operation(summary = "读取安全响应头", description = "返回 204，Content-Security-Policy 响应头为当前通用安全设置中的 CSP。")
    @GetMapping("/api/v1/security-headers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void securityHeaders() {
    }
}
