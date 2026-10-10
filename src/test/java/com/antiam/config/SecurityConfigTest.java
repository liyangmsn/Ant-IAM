package com.antiam.config;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antiam.repository.AuthenticationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(SecurityConfigTest.TestConfig.class)
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsAnonymousConsoleRequests() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsSignedInUserWithoutConsolePermission() throws Exception {
        mvc.perform(get("/api/v1/users").with(as())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events").with(as())).andExpect(status().isForbidden());
    }

    @Test
    void allowsSelfServiceEndpointsForAnySignedInUser() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(as())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me/console-access").with(as())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/access/me/applications").with(as())).andExpect(status().isOk());
    }

    @Test
    void readPermissionAllowsOnlyGetRequestsOfItsModule() throws Exception {
        RequestPostProcessor reader = as("iam:user:read");

        mvc.perform(get("/api/v1/users").with(reader)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/organizations/tree").with(reader)).andExpect(status().isOk());
        mvc.perform(get("/scim/v2/Users").with(reader)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/users").with(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/access/applications").with(reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/access/roles").with(reader)).andExpect(status().isForbidden());
    }

    @Test
    void writePermissionAllowsMutationsOfItsModule() throws Exception {
        RequestPostProcessor writer = as("iam:user:write", "iam:user:read");

        mvc.perform(post("/api/v1/users").with(writer)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/access/groups/7b0e7c3e-4b36-4a4b-9a55-2f4e4f7d8a11").with(writer)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/identity-sources").with(writer)).andExpect(status().isForbidden());
    }

    @Test
    void roleAssignmentsBelongToRoleModule() throws Exception {
        mvc.perform(post("/api/v1/users/role-assignments").with(as("iam:user:write", "iam:user:read")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/role-assignments").with(as("iam:role:write", "iam:role:read")))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/roles/7b0e7c3e-4b36-4a4b-9a55-2f4e4f7d8a11").with(as("iam:role:write")))
            .andExpect(status().isOk());
    }

    @Test
    void applicationPermissionEndpointsDeferToMethodSecurity() throws Exception {
        String base = "/api/v1/access/applications/7b0e7c3e-4b36-4a4b-9a55-2f4e4f7d8a11";

        // 委派管理员没有控制台权限，URL 层只要求登录，具体由 @PreAuthorize 判定
        mvc.perform(get(base + "/permission-roles").with(as())).andExpect(status().isOk());
        mvc.perform(post(base + "/permission-roles/x/members").with(as())).andExpect(status().isOk());
        mvc.perform(get(base + "/permission-roles")).andExpect(status().isUnauthorized());
        // 应用的其他配置仍按控制台模块授权
        mvc.perform(get(base + "/sso-config").with(as())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/access/me/managed-applications").with(as())).andExpect(status().isOk());
    }

    @Test
    void sourceScopedScimEndpointsSkipConsoleAuthorization() throws Exception {
        // 同步令牌在业务层校验；URL 层不能要求控制台会话，否则第三方无法调用
        mvc.perform(get("/scim/v2/sources/hr/Users")).andExpect(status().isOk());
        mvc.perform(post("/scim/v2/sources/hr/Users")).andExpect(status().isOk());
        // 全局 SCIM 仍要求用户模块权限
        mvc.perform(get("/scim/v2/Users")).andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyModulesRejectMutations() throws Exception {
        RequestPostProcessor auditor = as("iam:audit:read");

        mvc.perform(get("/api/v1/audit-events/export").with(auditor)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit-events").with(auditor)).andExpect(status().isForbidden());
    }

    @Test
    void unmappedConsoleEndpointsRequireIamAdmin() throws Exception {
        String[] everyPermission = ConsolePermission.codes().toArray(String[]::new);

        mvc.perform(get("/api/v1/unmapped").with(as(everyPermission))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/unmapped").with(as(SecurityAuthorities.IAM_ADMIN_AUTHORITY))).andExpect(status().isOk());
    }

    @Test
    void iamAdminCanReachEveryModule() throws Exception {
        RequestPostProcessor admin = as(SecurityAuthorities.IAM_ADMIN_AUTHORITY);

        mvc.perform(post("/api/v1/settings").with(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/access/role-permissions").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/summary").with(admin)).andExpect(status().isOk());
    }

    private static RequestPostProcessor as(String... authorities) {
        String[] all = new String[authorities.length + 1];
        all[0] = "ROLE_USER";
        System.arraycopy(authorities, 0, all, 1, authorities.length);
        return user("tester").authorities(java.util.Arrays.stream(all)
            .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
            .toList());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter() {
            return new SessionTokenAuthenticationFilter(mock(AuthenticationSessionRepository.class), mock(ConsoleAuthorityResolver.class));
        }

        @Bean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
            return new RestAuthenticationEntryPoint();
        }

        @Bean
        EchoController echoController() {
            return new EchoController();
        }
    }

    @RestController
    static class EchoController {

        @RequestMapping("/**")
        String echo() {
            return "ok";
        }
    }
}
