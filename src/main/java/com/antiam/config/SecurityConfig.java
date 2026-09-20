package com.antiam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    // 管理接口统一使用 Bearer session token；协议发现、token 端点和公开元数据按标准放行到业务层处理。
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter,
        RestAuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/.well-known/openid-configuration").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/jwks").permitAll()
                .requestMatchers(HttpMethod.GET, "/saml2/metadata").permitAll()
                .requestMatchers(HttpMethod.GET, "/saml2/metadata.xml").permitAll()
                .requestMatchers(HttpMethod.GET, "/cas/serviceValidate").permitAll()
                .requestMatchers(HttpMethod.GET, "/cas/p3/serviceValidate").permitAll()
                .requestMatchers(HttpMethod.POST, "/jwt/verify").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/token").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/introspect").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/revoke").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/password-reset-tickets/consumptions").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/sms-codes").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/mobile-login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/password-login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/synchronizer/event_receive/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/userinfo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/authentication/third-party/*/authorize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/third-party/*/callback").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(restAuthenticationEntryPoint))
            .addFilterBefore(sessionTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
