package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.config.SecurityAuthorities;
import com.antiam.domain.UserAccount;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminLevel;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDecisionResponse;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ApplicationDelegationServiceTest {

    private final AccessService access = mock(AccessService.class);
    private final ApplicationPermissionService applicationPermissions = mock(ApplicationPermissionService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final ApplicationDelegationService service = new ApplicationDelegationService(
        access,
        applicationPermissions,
        mock(ApplicationRepository.class),
        users,
        mock(UserGroupRepository.class),
        mock(OrganizationRepository.class));

    private final UUID applicationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void globalAdminsManageEveryApplication() {
        assertThat(service.levelFor(applicationId, auth("root", SecurityAuthorities.IAM_ADMIN_AUTHORITY))).isEqualTo(ApplicationAdminLevel.GLOBAL);
        assertThat(service.levelFor(applicationId, auth("ops", "iam:application:write"))).isEqualTo(ApplicationAdminLevel.GLOBAL);
    }

    @Test
    void delegatedLevelComesFromReservedPermissionsInsideTheApplication() {
        givenUserPermissions(true, "order:read", "iam:app:grant:manage");
        assertThat(service.levelFor(applicationId, auth("alice"))).isEqualTo(ApplicationAdminLevel.GRANT_MANAGER);

        givenUserPermissions(true, "iam:app:grant:manage", "iam:app:permission:manage");
        assertThat(service.levelFor(applicationId, auth("alice"))).isEqualTo(ApplicationAdminLevel.OWNER);
    }

    @Test
    void readOnlyConsoleAdminFallsBackToGlobalRead() {
        givenUserPermissions(true, "order:read");
        assertThat(service.levelFor(applicationId, auth("alice", "iam:application:read"))).isEqualTo(ApplicationAdminLevel.GLOBAL_READ);
        assertThat(service.levelFor(applicationId, auth("alice"))).isEqualTo(ApplicationAdminLevel.NONE);
    }

    @Test
    void delegationEndsWhenUserLosesApplicationAccess() {
        givenUserPermissions(false);
        assertThat(service.levelFor(applicationId, auth("alice"))).isEqualTo(ApplicationAdminLevel.NONE);
    }

    @Test
    void grantManagersCannotGrantDelegationRoles() {
        UUID normalRole = UUID.randomUUID();
        UUID ownerRole = UUID.randomUUID();
        when(applicationPermissions.roleGrantsDelegation(applicationId, normalRole)).thenReturn(false);
        when(applicationPermissions.roleGrantsDelegation(applicationId, ownerRole)).thenReturn(true);

        assertThat(service.canGrantRole(ApplicationAdminLevel.GRANT_MANAGER, applicationId, normalRole)).isTrue();
        assertThat(service.canGrantRole(ApplicationAdminLevel.GRANT_MANAGER, applicationId, ownerRole)).isFalse();
        assertThat(service.canGrantRole(ApplicationAdminLevel.OWNER, applicationId, ownerRole)).isTrue();
        assertThat(service.canGrantRole(ApplicationAdminLevel.GLOBAL_READ, applicationId, normalRole)).isFalse();
    }

    private void givenUserPermissions(boolean accessAllowed, String... codes) {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(userId);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(access.decideApplicationPermissions(applicationId, userId)).thenReturn(new ApplicationPermissionDecisionResponse(
            applicationId, userId, accessAllowed, accessAllowed ? "direct_assignment" : "no_assignment", List.of(codes)));
    }

    private static Authentication auth(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
