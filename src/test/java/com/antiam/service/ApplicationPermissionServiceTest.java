package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.common.ConflictException;
import com.antiam.common.ForbiddenException;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationPermission;
import com.antiam.domain.ApplicationPermissionRole;
import com.antiam.domain.ApplicationPermissionRoleMember;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDefinition;
import com.antiam.dto.ApplicationPermissionDtos.CreateApplicationPermissionRoleRequest;
import com.antiam.dto.ApplicationPermissionDtos.UpdateApplicationPermissionRoleRequest;
import com.antiam.repository.ApplicationPermissionRepository;
import com.antiam.repository.ApplicationPermissionRoleMemberRepository;
import com.antiam.repository.ApplicationPermissionRoleRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationPermissionServiceTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationPermissionRepository permissions = mock(ApplicationPermissionRepository.class);
    private final ApplicationPermissionRoleRepository roles = mock(ApplicationPermissionRoleRepository.class);
    private final ApplicationPermissionRoleMemberRepository members = mock(ApplicationPermissionRoleMemberRepository.class);
    private final ApplicationPermissionService service = new ApplicationPermissionService(
        applications,
        permissions,
        roles,
        members,
        mock(UserAccountRepository.class),
        mock(UserGroupRepository.class),
        mock(OrganizationRepository.class),
        mock(AuditService.class));

    @Test
    void syncCreatesUpdatesAndDeletesDeclaredPermissions() {
        UUID applicationId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermission kept = new ApplicationPermission(application, "order:read", "旧名称", null);
        ApplicationPermission removed = mock(ApplicationPermission.class);
        UUID removedId = UUID.randomUUID();
        when(removed.getId()).thenReturn(removedId);
        when(removed.getCode()).thenReturn("order:legacy");
        ApplicationPermissionRole role = mock(ApplicationPermissionRole.class);
        when(permissions.findByApplicationIdOrderByCodeAsc(applicationId)).thenReturn(List.of(kept, removed), List.of());
        when(roles.findByPermissionsId(removedId)).thenReturn(List.of(role));

        service.syncPermissions(application, List.of(
            new ApplicationPermissionDefinition("order:read", "查看订单", null),
            new ApplicationPermissionDefinition("order:approve", "审批订单", "审批待处理订单")), "admin");

        assertThat(kept.getName()).isEqualTo("查看订单");
        verify(permissions).save(any(ApplicationPermission.class));
        verify(role).revoke(removed);
        verify(permissions).delete(removed);
    }

    @Test
    void syncRejectsDuplicateCodes() {
        Application application = application(UUID.randomUUID());

        assertThatThrownBy(() -> service.syncPermissions(application, List.of(
            new ApplicationPermissionDefinition("order:read", "查看订单", null),
            new ApplicationPermissionDefinition("order:read", "查看订单2", null)), "admin"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order:read");
        verify(permissions, never()).save(any());
    }

    @Test
    void rejectsDuplicatePermissionCodeInSameApplication() {
        UUID applicationId = UUID.randomUUID();
        Application application = application(applicationId);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(permissions.existsByApplicationIdAndCode(applicationId, "order:read")).thenReturn(true);

        assertThatThrownBy(() -> service.createPermission(applicationId,
            new ApplicationPermissionDefinition("order:read", "查看订单", null), "admin"))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void effectivePermissionsUnionUserGroupAndOrganizationRoles() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID parentOrgId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermissionRole direct = role(application, permission(application, "order:read"));
        ApplicationPermissionRole viaGroup = role(application, permission(application, "order:approve"));
        ApplicationPermissionRole viaOrg = role(application, permission(application, "report:export"));
        ApplicationPermissionRole otherUser = role(application, permission(application, "admin:all"));
        List<ApplicationPermissionRoleMember> granted = List.of(
            ApplicationPermissionRoleMember.ofUser(direct, user(userId)),
            ApplicationPermissionRoleMember.ofGroup(viaGroup, group(groupId)),
            ApplicationPermissionRoleMember.ofOrganization(viaOrg, organization(parentOrgId)),
            ApplicationPermissionRoleMember.ofUser(otherUser, user(UUID.randomUUID())));
        when(members.findByApplicationIdWithPermissions(applicationId)).thenReturn(granted);

        Set<String> codes = service.effectivePermissionCodes(applicationId, userId, Set.of(groupId), Set.of(UUID.randomUUID(), parentOrgId));

        assertThat(codes).containsExactly("order:approve", "order:read", "report:export");
    }

    @Test
    void rejectsReservedPrefixWhenCreatingOrSyncing() {
        UUID applicationId = UUID.randomUUID();
        Application application = application(applicationId);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.createPermission(applicationId,
            new ApplicationPermissionDefinition("iam:app:permission:manage", "伪造", null), "admin"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.syncPermissions(application,
            List.of(new ApplicationPermissionDefinition("iam:anything", "伪造", null)), "client"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(permissions, never()).save(any());
    }

    @Test
    void syncLeavesReservedPermissionsUntouched() {
        UUID applicationId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermission reserved = new ApplicationPermission(application, "iam:app:grant:manage", "分配应用角色", null, true);
        when(permissions.findByApplicationIdOrderByCodeAsc(applicationId)).thenReturn(List.of(reserved), List.of(reserved));

        service.syncPermissions(application, List.of(), "client");

        verify(permissions, never()).delete(any());
    }

    @Test
    void reservedPermissionsAndBuiltInRolesAreImmutable() {
        UUID applicationId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermission reserved = new ApplicationPermission(application, "iam:app:grant:manage", "分配应用角色", null, true);
        ApplicationPermissionRole builtIn = new ApplicationPermissionRole(application, "iam:app-owner", "应用权限负责人", null, true);
        when(permissions.findByIdAndApplicationId(permissionId, applicationId)).thenReturn(Optional.of(reserved));
        when(roles.findByIdAndApplicationId(roleId, applicationId)).thenReturn(Optional.of(builtIn));

        assertThatThrownBy(() -> service.deletePermission(applicationId, permissionId, "admin")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.updateRole(applicationId, roleId,
            new UpdateApplicationPermissionRoleRequest("改名", null, List.of()), "admin")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.deleteRole(applicationId, roleId, "admin")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void normalRolesCannotIncludeReservedPermissions() {
        UUID applicationId = UUID.randomUUID();
        UUID reservedId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermission reserved = new ApplicationPermission(application, "iam:app:permission:manage", "管理应用权限", null, true);
        when(applications.findById(applicationId)).thenReturn(Optional.of(application));
        when(permissions.findAllById(Set.of(reservedId))).thenReturn(List.of(reserved));

        assertThatThrownBy(() -> service.createRole(applicationId,
            new CreateApplicationPermissionRoleRequest("sneaky", "偷偷提权", null, List.of(reservedId)), "owner"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void protectsTheLastApplicationOwner() {
        UUID applicationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Application application = application(applicationId);
        ApplicationPermissionRole owner = new ApplicationPermissionRole(application, "iam:app-owner", "应用权限负责人", null, true);
        ApplicationPermissionRoleMember member = ApplicationPermissionRoleMember.ofUser(owner, user(UUID.randomUUID()));
        when(roles.findByIdAndApplicationId(roleId, applicationId)).thenReturn(Optional.of(owner));
        when(members.findByIdAndRoleId(memberId, roleId)).thenReturn(Optional.of(member));
        when(members.countByRoleId(roleId)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeRoleMember(applicationId, roleId, memberId, "owner", true, null))
            .isInstanceOf(ConflictException.class);
        service.removeRoleMember(applicationId, roleId, memberId, "admin", false, null);
        verify(members).delete(member);
    }

    @Test
    void provisionsReservedPermissionsAndBuiltInRolesOnce() {
        UUID applicationId = UUID.randomUUID();
        Application application = application(applicationId);
        when(permissions.findByApplicationIdAndCode(any(), any())).thenReturn(Optional.empty());
        when(permissions.save(any(ApplicationPermission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roles.existsByApplicationIdAndCode(applicationId, "iam:app-owner")).thenReturn(false);
        when(roles.existsByApplicationIdAndCode(applicationId, "iam:app-grant-manager")).thenReturn(true);

        service.provisionDelegation(application);

        verify(permissions, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.argThat(ApplicationPermission::isReserved));
        verify(roles).save(org.mockito.ArgumentMatchers.argThat(role -> role.isBuiltIn() && role.getCode().equals("iam:app-owner") && role.grantsDelegation()));
    }

    private static Application application(UUID id) {
        Application application = mock(Application.class);
        when(application.getId()).thenReturn(id);
        return application;
    }

    private static ApplicationPermission permission(Application application, String code) {
        return new ApplicationPermission(application, code, code, null);
    }

    private static ApplicationPermissionRole role(Application application, ApplicationPermission permission) {
        ApplicationPermissionRole role = new ApplicationPermissionRole(application, permission.getCode(), permission.getCode(), null);
        role.replacePermissions(List.of(permission));
        return role;
    }

    private static UserAccount user(UUID id) {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private static UserGroup group(UUID id) {
        UserGroup group = mock(UserGroup.class);
        when(group.getId()).thenReturn(id);
        return group;
    }

    private static Organization organization(UUID id) {
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(id);
        return organization;
    }
}
