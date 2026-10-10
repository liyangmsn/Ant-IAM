package com.antiam.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // 顺序敏感：更具体的路径必须排在前面，例如 /api/v1/users/role-assignments 归属角色模块。
    static final List<ConsoleRoute> CONSOLE_ROUTES = List.of(
        new ConsoleRoute(ConsolePermission.ROLE_READ, ConsolePermission.ROLE_WRITE,
            "/api/v1/access/permissions", "/api/v1/access/permissions/**",
            "/api/v1/access/roles", "/api/v1/access/roles/**",
            "/api/v1/access/role-permissions", "/api/v1/access/group-roles",
            "/api/v1/permissions", "/api/v1/permissions/**",
            "/api/v1/roles", "/api/v1/roles/**",
            "/api/v1/users/role-assignments"),
        new ConsoleRoute(ConsolePermission.USER_READ, ConsolePermission.USER_WRITE,
            "/api/v1/users", "/api/v1/users/**",
            "/api/v1/organizations", "/api/v1/organizations/**",
            "/api/v1/access/groups", "/api/v1/access/groups/**",
            "/api/v1/groups", "/api/v1/groups/**",
            "/api/v1/access/users/**",
            "/api/v1/access/organizations/**",
            "/scim/v2/**"),
        new ConsoleRoute(ConsolePermission.APPLICATION_READ, ConsolePermission.APPLICATION_WRITE,
            "/api/v1/access/applications", "/api/v1/access/applications/**",
            "/api/v1/access/application-groups", "/api/v1/access/application-groups/**",
            "/api/v1/access/application-roles",
            "/api/v1/access/application-access-requests", "/api/v1/access/application-access-requests/**",
            "/api/v1/oauth/tokens", "/api/v1/oauth/tokens/**",
            "/api/v1/jwt-signing-keys", "/api/v1/jwt-signing-keys/**"),
        new ConsoleRoute(ConsolePermission.IDENTITY_SOURCE_READ, ConsolePermission.IDENTITY_SOURCE_WRITE,
            "/api/v1/identity-sources", "/api/v1/identity-sources/**"),
        new ConsoleRoute(ConsolePermission.AUTHENTICATION_READ, ConsolePermission.AUTHENTICATION_WRITE,
            "/api/v1/authentication-providers", "/api/v1/authentication-providers/**",
            "/api/v1/authentication-policies", "/api/v1/authentication-policies/**",
            "/api/v1/authentication/sessions", "/api/v1/authentication/sessions/**",
            "/api/v1/authentication/events"),
        new ConsoleRoute(ConsolePermission.SECURITY_READ, ConsolePermission.SECURITY_WRITE,
            "/api/v1/security-settings", "/api/v1/security-settings/**",
            "/api/v1/risk/**"),
        new ConsoleRoute(ConsolePermission.SYSTEM_READ, ConsolePermission.SYSTEM_WRITE,
            "/api/v1/settings", "/api/v1/settings/**",
            "/api/v1/tenants", "/api/v1/tenants/**",
            "/api/v1/files"),
        new ConsoleRoute(ConsolePermission.DASHBOARD_READ, null, "/api/v1/dashboard/**"),
        new ConsoleRoute(ConsolePermission.AUDIT_READ, null, "/api/v1/audit-events", "/api/v1/audit-events/**"));

    @Bean
    // 管理接口统一使用 Bearer session token；协议发现、token 端点和公开元数据按标准放行到业务层处理。
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter,
        RestAuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers(HttpMethod.GET, "/.well-known/openid-configuration").permitAll()
                    .requestMatchers(HttpMethod.GET, "/oauth2/jwks").permitAll()
                    .requestMatchers(HttpMethod.GET, "/saml2/metadata").permitAll()
                    .requestMatchers(HttpMethod.GET, "/saml2/metadata.xml").permitAll()
                    .requestMatchers(HttpMethod.GET, "/cas/login", "/cas/logout", "/cas/validate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/cas/serviceValidate", "/cas/proxyValidate").permitAll()
                    .requestMatchers(HttpMethod.GET, "/cas/p3/serviceValidate", "/cas/p3/proxyValidate").permitAll()
                    .requestMatchers(HttpMethod.POST, "/jwt/verify").permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth2/token").permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth2/introspect").permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth2/revoke").permitAll()
                    .requestMatchers(HttpMethod.GET, "/oauth2/permissions").permitAll()
                    .requestMatchers(HttpMethod.PUT, "/oauth2/permissions").permitAll()
                    .requestMatchers(HttpMethod.POST, "/oauth2/permissions/check").permitAll()
                    .requestMatchers("/oauth2/permission-admin", "/oauth2/permission-admin/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/users/password-reset-tickets/consumptions").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/sms-codes").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/mobile-login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/password-login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/mfa-login", "/api/v1/authentication/mfa-login/switch").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/synchronizer/event_receive/*").permitAll()
                    // 身份源 SCIM 端点使用同步令牌，在业务层按身份源校验，不走控制台会话。
                    .requestMatchers("/scim/v2/sources/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/oauth2/userinfo").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/security-headers").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/authentication-providers/public").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/authentication/third-party/*/authorize").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/third-party/*/callback").permitAll()
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**").authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/*").authenticated()
                    .requestMatchers(
                        "/api/v1/users/*/mfa-factors",
                        "/api/v1/users/*/mfa-factors/**",
                        "/api/v1/users/*/mfa-recovery-codes",
                        "/api/v1/users/*/mfa-challenges",
                        "/api/v1/users/*/mfa-challenges/**",
                        "/api/v1/users/*/third-party-bindings",
                        "/api/v1/users/*/third-party-bindings/**",
                        "/api/v1/access/me/**"
                    ).authenticated()
                    // 应用内权限可委派给应用自己的管理员，不按控制台模块授权，改由方法级 @PreAuthorize 判定。
                    .requestMatchers(
                        "/api/v1/access/applications/*/permissions",
                        "/api/v1/access/applications/*/permissions/**",
                        "/api/v1/access/applications/*/permission-roles",
                        "/api/v1/access/applications/*/permission-roles/**",
                        "/api/v1/access/applications/*/permission-decisions",
                        "/api/v1/access/applications/*/admin-access",
                        "/api/v1/access/applications/*/grantable-subjects"
                    ).authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/authentication/sessions", "/api/v1/authentication/events").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/sessions/*/end").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/authentication/logout").permitAll();
                // 控制台接口按模块授权：GET 需要 read 权限点，其余方法需要 write 权限点；IAM 管理员拥有全部模块。
                for (ConsoleRoute route : CONSOLE_ROUTES) {
                    auth.requestMatchers(HttpMethod.GET, route.patterns())
                        .hasAnyAuthority(SecurityAuthorities.IAM_ADMIN_AUTHORITY, route.read().code());
                    if (route.write() != null) {
                        auth.requestMatchers(route.patterns())
                            .hasAnyAuthority(SecurityAuthorities.IAM_ADMIN_AUTHORITY, route.write().code());
                    }
                }
                auth
                    .requestMatchers("/api/v1/**", "/scim/v2/**").hasAuthority(SecurityAuthorities.IAM_ADMIN_AUTHORITY)
                    .anyRequest().authenticated();
            })
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(restAuthenticationEntryPoint))
            .addFilterBefore(sessionTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    record ConsoleRoute(ConsolePermission read, ConsolePermission write, String... patterns) {
    }
}
