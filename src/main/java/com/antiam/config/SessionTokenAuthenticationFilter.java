package com.antiam.config;

import com.antiam.domain.AuthenticationSession;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.service.LoginSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    // 受限会话（需修改密码或绑定 MFA）只允许访问个人资料、MFA 管理、会话和公开接口。
    private static final List<String> RESTRICTED_ALLOWED_PATTERNS = List.of(
        "/api/v1/users/me",
        "/api/v1/users/me/**",
        "/api/v1/users/{userId}/mfa-factors",
        "/api/v1/users/{userId}/mfa-factors/**",
        "/api/v1/users/{userId}/mfa-recovery-codes",
        "/api/v1/users/{userId}/mfa-challenges",
        "/api/v1/users/{userId}/mfa-challenges/**",
        "/api/v1/authentication/sessions",
        "/api/v1/authentication/sessions/**",
        "/api/v1/authentication/logout",
        "/api/v1/catalog",
        "/api/v1/authentication-providers/public");

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AuthenticationSessionRepository sessions;
    private final ConsoleAuthorityResolver consoleAuthorities;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<AuthenticationSession> resolved = sessions.findBySessionIndexAndActive(token, true)
                .filter(session -> session.getUser() != null)
                .filter(session -> session.getExpiresAt() == null || session.getExpiresAt().isAfter(Instant.now()))
                .filter(session -> LoginSessionService.signInBlockReason(session.getUser()) == null);
            if (resolved.isPresent()) {
                AuthenticationSession session = resolved.get();
                if (session.getRestriction() != null && !restrictedAllowed(request, session)) {
                    writeRestricted(response, session);
                    return;
                }
                String username = session.getUser().getUsername();
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, consoleAuthorities.authorities(username));
                authentication.setDetails(session.getId());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean restrictedAllowed(HttpServletRequest request, AuthenticationSession session) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String ownId = session.getUser().getId().toString();
        for (String pattern : RESTRICTED_ALLOWED_PATTERNS) {
            if (!PATH_MATCHER.match(pattern, path)) {
                continue;
            }
            if (!pattern.contains("{userId}")) {
                return true;
            }
            return ownId.equalsIgnoreCase(PATH_MATCHER.extractUriTemplateVariables(pattern, path).get("userId"));
        }
        return false;
    }

    private void writeRestricted(HttpServletResponse response, AuthenticationSession session) throws IOException {
        String message = switch (session.getRestriction()) {
            case PASSWORD_CHANGE -> "请先修改密码后再继续访问";
            case MFA_ENROLLMENT -> "请先绑定多因素认证后再继续访问";
        };
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"urn:ant-iam:error:session-restricted\",\"title\":\"Forbidden\",\"status\":403,"
            + "\"detail\":\"" + message + "\",\"restriction\":\"" + session.getRestriction().name() + "\"}");
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }
}
