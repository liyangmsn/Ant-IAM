package com.antiam.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一解析协议端点的 issuer：优先使用 iam.issuer 配置的对外地址，未配置时按请求（含 X-Forwarded-* 头）推导。
 */
@Component
public class IssuerResolver {

    private final String configuredIssuer;

    public IssuerResolver(@Value("${iam.issuer:}") String configuredIssuer) {
        this.configuredIssuer = trimTrailingSlash(configuredIssuer);
    }

    public String resolve(HttpServletRequest request) {
        if (!configuredIssuer.isBlank()) {
            return configuredIssuer;
        }
        String url = request.getRequestURL().toString();
        String origin = url.substring(0, url.length() - request.getRequestURI().length());
        return trimTrailingSlash(origin + request.getContextPath());
    }

    /**
     * 是否显式配置了 issuer；只有显式配置时才能对外部签发的令牌做严格的 iss 比对。
     */
    public boolean isConfigured() {
        return !configuredIssuer.isBlank();
    }

    public String configuredIssuer() {
        return configuredIssuer;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
