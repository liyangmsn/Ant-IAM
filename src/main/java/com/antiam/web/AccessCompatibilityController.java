package com.antiam.web;

import static com.antiam.dto.AccessDtos.CreateGroupRequest;
import static com.antiam.dto.AccessDtos.CreatePermissionRequest;
import static com.antiam.dto.AccessDtos.CreateRoleRequest;
import static com.antiam.dto.AccessDtos.AddGroupMemberRequest;
import static com.antiam.dto.AccessDtos.GroupResponse;
import static com.antiam.dto.AccessDtos.GroupMemberResponse;
import static com.antiam.dto.AccessDtos.PermissionResponse;
import static com.antiam.dto.AccessDtos.RoleResponse;
import static com.antiam.dto.AccessDtos.UpdateGroupRequest;
import static com.antiam.dto.AccessDtos.UpdatePermissionRequest;
import static com.antiam.dto.AccessDtos.UpdateRoleRequest;

import com.antiam.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "TOPIAM 兼容入口", description = "为前端 TOPIAM 风格资源路径提供访问控制资源别名")
public class AccessCompatibilityController {

    private final AccessService access;

    @Operation(summary = "查询权限列表（兼容路径）", description = "等价于 /api/v1/access/permissions。")
    @GetMapping("/api/v1/permissions")
    List<PermissionResponse> permissions(
        @Parameter(description = "关键字，匹配权限编码、名称或描述") @RequestParam(required = false) String keyword
    ) {
        return access.listPermissions(keyword);
    }

    @Operation(summary = "创建权限（兼容路径）", description = "等价于 /api/v1/access/permissions。")
    @PostMapping("/api/v1/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    PermissionResponse createPermission(@Valid @RequestBody CreatePermissionRequest request, Principal principal) {
        return access.createPermission(request, principal.getName());
    }

    @Operation(summary = "获取权限详情（兼容路径）", description = "等价于 /api/v1/access/permissions/{permissionId}。")
    @GetMapping("/api/v1/permissions/{permissionId}")
    PermissionResponse permission(@PathVariable UUID permissionId) {
        return access.getPermission(permissionId);
    }

    @Operation(summary = "更新权限（兼容路径）", description = "等价于 /api/v1/access/permissions/{permissionId}。")
    @PutMapping("/api/v1/permissions/{permissionId}")
    PermissionResponse updatePermission(
        @PathVariable UUID permissionId,
        @Valid @RequestBody UpdatePermissionRequest request,
        Principal principal
    ) {
        return access.updatePermission(permissionId, request, principal.getName());
    }

    @Operation(summary = "查询角色列表（兼容路径）", description = "等价于 /api/v1/access/roles。")
    @GetMapping("/api/v1/roles")
    List<RoleResponse> roles(
        @Parameter(description = "关键字，匹配角色编码、名称或描述") @RequestParam(required = false) String keyword
    ) {
        return access.listRoles(keyword);
    }

    @Operation(summary = "创建角色（兼容路径）", description = "等价于 /api/v1/access/roles。")
    @PostMapping("/api/v1/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request, Principal principal) {
        return access.createRole(request, principal.getName());
    }

    @Operation(summary = "获取角色详情（兼容路径）", description = "等价于 /api/v1/access/roles/{roleId}。")
    @GetMapping("/api/v1/roles/{roleId}")
    RoleResponse role(@PathVariable UUID roleId) {
        return access.getRole(roleId);
    }

    @Operation(summary = "更新角色（兼容路径）", description = "等价于 /api/v1/access/roles/{roleId}。")
    @PutMapping("/api/v1/roles/{roleId}")
    RoleResponse updateRole(
        @PathVariable UUID roleId,
        @Valid @RequestBody UpdateRoleRequest request,
        Principal principal
    ) {
        return access.updateRole(roleId, request, principal.getName());
    }

    @Operation(summary = "查询用户组列表（兼容路径）", description = "等价于 /api/v1/access/groups。")
    @GetMapping("/api/v1/groups")
    List<GroupResponse> groups(
        @Parameter(description = "关键字，匹配用户组编码或名称") @RequestParam(required = false) String keyword
    ) {
        return access.listGroups(keyword);
    }

    @Operation(summary = "创建用户组（兼容路径）", description = "等价于 /api/v1/access/groups。")
    @PostMapping("/api/v1/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request, Principal principal) {
        return access.createGroup(request, principal.getName());
    }

    @Operation(summary = "获取用户组详情（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}。")
    @GetMapping("/api/v1/groups/{groupId}")
    GroupResponse group(@PathVariable UUID groupId) {
        return access.getGroup(groupId);
    }

    @Operation(summary = "更新用户组（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}。")
    @PutMapping("/api/v1/groups/{groupId}")
    GroupResponse updateGroup(
        @PathVariable UUID groupId,
        @Valid @RequestBody UpdateGroupRequest request,
        Principal principal
    ) {
        return access.updateGroup(groupId, request, principal.getName());
    }

    @Operation(summary = "删除用户组（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}。")
    @DeleteMapping("/api/v1/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(@PathVariable UUID groupId, Principal principal) {
        access.deleteGroup(groupId, principal.getName());
    }

    @Operation(summary = "查询用户组成员（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}/members。")
    @GetMapping("/api/v1/groups/{groupId}/members")
    List<GroupMemberResponse> groupMembers(@PathVariable UUID groupId) {
        return access.listGroupMembers(groupId);
    }

    @Operation(summary = "添加用户组成员（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}/members。")
    @PostMapping("/api/v1/groups/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    GroupMemberResponse addGroupMember(
        @PathVariable UUID groupId,
        @Valid @RequestBody AddGroupMemberRequest request,
        Principal principal
    ) {
        return access.addGroupMember(groupId, request.userId(), principal.getName());
    }

    @Operation(summary = "移除用户组成员（兼容路径）", description = "等价于 /api/v1/access/groups/{groupId}/members/{userId}。")
    @DeleteMapping("/api/v1/groups/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID userId,
        Principal principal
    ) {
        access.removeGroupMember(groupId, userId, principal.getName());
    }
}
