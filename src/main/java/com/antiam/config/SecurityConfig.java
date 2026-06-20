package com.antiam.config;

import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    // 配置管理接口默认需要 Basic Auth；协议发现、token 端点和公开元数据按标准放行到业务层处理。
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter) throws Exception {
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
                .requestMatchers(HttpMethod.POST, "/oauth2/token").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/introspect").permitAll()
                .requestMatchers(HttpMethod.POST, "/oauth2/revoke").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/password-reset-tickets/consumptions").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/sms-codes").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/mobile-login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/password-login").permitAll()
                .requestMatchers(HttpMethod.GET, "/oauth2/userinfo").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/authentication/third-party/*/authorize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/authentication/third-party/*/callback").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(sessionTokenAuthenticationFilter, BasicAuthenticationFilter.class)
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties security, PasswordEncoder passwordEncoder) {
        SecurityProperties.User configuredUser = security.getUser();
        return new InMemoryUserDetailsManager(User.withUsername(configuredUser.getName())
            .password(passwordEncoder.encode(configuredUser.getPassword()))
            .roles(configuredUser.getRoles().toArray(String[]::new))
            .build());
    }
}
