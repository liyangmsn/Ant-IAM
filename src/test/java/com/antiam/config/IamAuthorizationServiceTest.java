package com.antiam.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.Permission;
import com.antiam.domain.UserAccount;
import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.PermissionRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

class IamAuthorizationServiceTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserGroupRepository groups = mock(UserGroupRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final IamAuthorizationService authorization = new IamAuthorizationService(
        users,
        roles,
        groups,
        permissions,
        mock(AuthenticationSessionRepository.class),
        mock(OAuthConsentRepository.class),
        mock(com.antiam.service.ApplicationDelegationService.class));

    private final Authentication admin = authentication("admin", SecurityAuthorities.IAM_ADMIN_AUTHORITY);
    private final Authentication userManager = authentication("manager", "iam:user:read", "iam:user:write");
    private final Authentication roleManager = authentication("role-manager", "iam:role:read", "iam:role:write");

    @Test
    void userManagerCannotAdministerConsoleUsers() {
        UUID ordinaryUser = UUID.randomUUID();
        UUID consoleUser = UUID.randomUUID();
        when(users.findByUsername("manager")).thenReturn(Optional.empty());
        when(users.holdsConsoleAccess(eq(ordinaryUser), anyString(), anyString())).thenReturn(false);
        when(users.holdsConsoleAccess(eq(consoleUser), anyString(), anyString())).thenReturn(true);

        assertThat(authorization.canAdministerUser(ordinaryUser, userManager)).isTrue();
        assertThat(authorization.canAdministerUser(consoleUser, userManager)).isFalse();
        assertThat(authorization.canManageUser(consoleUser, userManager)).isFalse();
        assertThat(authorization.canAdministerUser(consoleUser, admin)).isTrue();
    }

    @Test
    void usersCanManageThemselvesWithoutConsolePermission() {
        UUID selfId = UUID.randomUUID();
        UserAccount self = new UserAccount("alice", "Alice", null, null, null, null);
        ReflectionTestUtils.setField(self, "id", selfId);
        when(users.findByUsername("alice")).thenReturn(Optional.of(self));
        Authentication alice = authentication("alice");

        assertThat(authorization.canManageUser(selfId, alice)).isTrue();
        assertThat(authorization.canReadUser(selfId, alice)).isTrue();
        assertThat(authorization.canAdministerUser(selfId, alice)).isFalse();
        assertThat(authorization.canManageUser(UUID.randomUUID(), alice)).isFalse();
    }

    @Test
    void onlyIamAdminCanCreateAdminUsers() {
        assertThat(authorization.canCreateUser("user", userManager)).isTrue();
        assertThat(authorization.canCreateUser("admin", userManager)).isFalse();
        assertThat(authorization.canCreateUser("admin", admin)).isTrue();
    }

    @Test
    void roleManagerCannotTouchConsolePermissionsOrPrivilegedRoles() {
        UUID ordinaryRole = UUID.randomUUID();
        UUID privilegedRole = UUID.randomUUID();
        UUID ordinaryPermission = UUID.randomUUID();
        UUID consolePermission = UUID.randomUUID();
        when(roles.holdsConsoleAccess(eq(ordinaryRole), anyString(), anyString())).thenReturn(false);
        when(roles.holdsConsoleAccess(eq(privilegedRole), anyString(), anyString())).thenReturn(true);
        when(permissions.findById(ordinaryPermission)).thenReturn(Optional.of(new Permission("crm:order:read", "查看订单", null)));
        when(permissions.findById(consolePermission)).thenReturn(Optional.of(new Permission("iam:user:write", "管理用户", null)));

        assertThat(authorization.canGrantPermissionToRole(ordinaryRole, ordinaryPermission, roleManager)).isTrue();
        assertThat(authorization.canGrantPermissionToRole(ordinaryRole, consolePermission, roleManager)).isFalse();
        assertThat(authorization.canGrantPermissionToRole(privilegedRole, ordinaryPermission, roleManager)).isFalse();
        assertThat(authorization.canManageRole(privilegedRole, roleManager)).isFalse();
        assertThat(authorization.canCreatePermission("iam:custom:read", roleManager)).isFalse();
        assertThat(authorization.canCreatePermission("crm:order:write", roleManager)).isTrue();
        assertThat(authorization.canGrantPermissionToRole(privilegedRole, consolePermission, admin)).isTrue();
        assertThat(authorization.canManageRole(ordinaryRole, userManager)).isFalse();
    }

    @Test
    void userManagerCannotChangeMembershipOfPrivilegedGroups() {
        UUID ordinaryGroup = UUID.randomUUID();
        UUID privilegedGroup = UUID.randomUUID();
        when(groups.holdsConsoleAccess(eq(ordinaryGroup), anyString(), anyString())).thenReturn(false);
        when(groups.holdsConsoleAccess(eq(privilegedGroup), anyString(), anyString())).thenReturn(true);

        assertThat(authorization.canManageGroupMembership(ordinaryGroup, userManager)).isTrue();
        assertThat(authorization.canManageGroupMembership(privilegedGroup, userManager)).isFalse();
        assertThat(authorization.canManageGroupMembership(privilegedGroup, admin)).isTrue();
        assertThat(authorization.canManageGroupMembership(ordinaryGroup, roleManager)).isFalse();
    }

    @Test
    void consolePermissionPatternMatchesOnlyIamCodes() {
        when(users.holdsConsoleAccess(any(), eq(SecurityAuthorities.IAM_ADMIN_ROLE), eq("iam:%"))).thenReturn(true);

        assertThat(authorization.canAdministerUser(UUID.randomUUID(), userManager)).isFalse();
    }

    private static Authentication authentication(String username, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(
            username,
            null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
