package com.antiam.config;

import com.antiam.repository.AuthenticationSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationSessionRepository sessions;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            sessions.findBySessionIndexAndActive(token, true)
                .filter(session -> session.getUser() != null)
                .filter(session -> session.getExpiresAt() == null || session.getExpiresAt().isAfter(Instant.now()))
                .ifPresent(session -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    session.getUser().getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")))));
        }
        filterChain.doFilter(request, response);
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
