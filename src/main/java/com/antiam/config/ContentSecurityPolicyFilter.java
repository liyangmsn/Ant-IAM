package com.antiam.config;

import com.antiam.service.SecuritySettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 将通用安全设置中的 CSP 写入后端响应；前端静态页由 nginx 通过 /api/v1/security-headers 取同一份策略。
 */
@Component
@RequiredArgsConstructor
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    private final SecuritySettingService securitySettings;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String policy = securitySettings.contentSecurityPolicy();
        if (!policy.isBlank()) {
            response.setHeader("Content-Security-Policy", policy);
        }
        filterChain.doFilter(request, response);
    }
}
