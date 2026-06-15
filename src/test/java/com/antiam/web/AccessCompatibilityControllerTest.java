package com.antiam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.AccountStatus;
import com.antiam.dto.AccessDtos.AddGroupMemberRequest;
import com.antiam.dto.AccessDtos.GroupMemberResponse;
import com.antiam.dto.AccessDtos.GroupResponse;
import com.antiam.dto.AccessDtos.PermissionResponse;
import com.antiam.dto.AccessDtos.RoleResponse;
import com.antiam.service.AccessService;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCompatibilityControllerTest {

    private final AccessService access = mock(AccessService.class);
    private final AccessCompatibilityController controller = new AccessCompatibilityController(access);
    private final Principal principal = () -> "admin";

    @Test
    void exposesPermissionAlias() {
        PermissionResponse permission = new PermissionResponse(UUID.randomUUID(), "user:read", "查看用户", null);
        when(access.listPermissions("user")).thenReturn(List.of(permission));

        assertThat(controller.permissions("user")).containsExactly(permission);

        verify(access).listPermissions("user");
    }

    @Test
    void exposesRoleAlias() {
        RoleResponse role = new RoleResponse(UUID.randomUUID(), "iam_admin", "IAM 管理员", null, Set.of("user:read"));
        when(access.listRoles("admin")).thenReturn(List.of(role));

        assertThat(controller.roles("admin")).containsExactly(role);

        verify(access).listRoles("admin");
    }

    @Test
    void exposesGroupAlias() {
        GroupResponse group = new GroupResponse(UUID.randomUUID(), "engineering", "研发部", "研发用户组", Set.of("iam_admin"), 3, null, null);
        when(access.listGroups("engineering")).thenReturn(List.of(group));

        assertThat(controller.groups("engineering")).containsExactly(group);

        verify(access).listGroups("engineering");
    }

    @Test
    void exposesGroupMembersAlias() {
        UUID groupId = UUID.randomUUID();
        GroupMemberResponse member = new GroupMemberResponse(
            UUID.randomUUID(),
            "alice",
            "Alice",
            "alice@example.com",
            AccountStatus.ACTIVE,
            null,
            null);
        when(access.listGroupMembers(groupId)).thenReturn(List.of(member));

        assertThat(controller.groupMembers(groupId)).containsExactly(member);

        verify(access).listGroupMembers(groupId);
    }

    @Test
    void exposesDeleteGroupAlias() {
        UUID groupId = UUID.randomUUID();

        controller.deleteGroup(groupId, principal);

        verify(access).deleteGroup(groupId, "admin");
    }

    @Test
    void exposesAddGroupMemberAlias() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        GroupMemberResponse member = new GroupMemberResponse(
            userId,
            "alice",
            "Alice",
            "alice@example.com",
            AccountStatus.ACTIVE,
            null,
            null);
        when(access.addGroupMember(groupId, userId, "admin")).thenReturn(member);

        assertThat(controller.addGroupMember(groupId, new AddGroupMemberRequest(userId), principal)).isEqualTo(member);

        verify(access).addGroupMember(groupId, userId, "admin");
    }

    @Test
    void exposesRemoveGroupMemberAlias() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        controller.removeGroupMember(groupId, userId, principal);

        verify(access).removeGroupMember(groupId, userId, "admin");
    }
}
