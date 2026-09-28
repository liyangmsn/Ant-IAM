package com.antiam.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.repository.UserAccountRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class ConsoleAuthorityResolverTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final ConsoleAuthorityResolver resolver = new ConsoleAuthorityResolver(users);

    @Test
    void grantsEveryConsolePermissionToIamAdmin() {
        when(users.findEffectiveRoleCodesByUsername("admin")).thenReturn(Set.of(SecurityAuthorities.IAM_ADMIN_ROLE));

        ConsoleAuthorityResolver.ConsoleAccess access = resolver.resolve("admin");

        assertThat(access.superAdmin()).isTrue();
        assertThat(access.permissions()).containsExactlyElementsOf(ConsolePermission.codes());
        assertThat(resolver.authorities("admin")).extracting(GrantedAuthority::getAuthority)
            .contains("ROLE_USER", SecurityAuthorities.IAM_ADMIN_AUTHORITY);
    }

    @Test
    void writePermissionImpliesReadAndIgnoresNonConsoleCodes() {
        when(users.findEffectiveRoleCodesByUsername("operator")).thenReturn(Set.of("operator"));
        when(users.findEffectivePermissionCodesByUsername("operator"))
            .thenReturn(Set.of("iam:user:write", "iam:audit:read", "iam:unknown:write", "crm:order:read"));

        ConsoleAuthorityResolver.ConsoleAccess access = resolver.resolve("operator");

        assertThat(access.superAdmin()).isFalse();
        assertThat(access.permissions()).containsExactlyInAnyOrder("iam:user:read", "iam:user:write", "iam:audit:read");
        assertThat(resolver.authorities("operator")).extracting(GrantedAuthority::getAuthority)
            .doesNotContain(SecurityAuthorities.IAM_ADMIN_AUTHORITY);
    }

    @Test
    void ordinaryUserOnlyGetsRoleUser() {
        when(users.findEffectiveRoleCodesByUsername("alice")).thenReturn(Set.of());
        when(users.findEffectivePermissionCodesByUsername("alice")).thenReturn(Set.of());

        assertThat(resolver.authorities("alice")).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }
}
