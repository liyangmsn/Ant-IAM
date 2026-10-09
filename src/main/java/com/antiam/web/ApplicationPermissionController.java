package com.antiam.web;

import com.antiam.dto.AccessDtos.ApplicationAssignmentSubjectType;
import com.antiam.dto.ApplicationPermissionDtos.AddApplicationPermissionRoleMembersRequest;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminAccessResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminLevel;
import com.antiam.dto.ApplicationPermissionDtos.GrantableSubjectResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDecisionResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDefinition;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleMemberResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleResponse;
import com.antiam.dto.ApplicationPermissionDtos.CreateApplicationPermissionRoleRequest;
import com.antiam.dto.ApplicationPermissionDtos.UpdateApplicationPermissionRequest;
import com.antiam.dto.ApplicationPermissionDtos.UpdateApplicationPermissionRoleRequest;
import com.antiam.service.AccessService;
import com.antiam.service.ApplicationDelegationService;
import com.antiam.service.ApplicationPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用内权限的管理接口，控制台与门户共用：IAM 管理员可管理全部应用，应用权限负责人和授权管理员只能管理被委派的应用。
 */
@Tag(name = "应用内权限", description = "应用内权限点、应用内角色与授权管理")
@RestController
@RequestMapping("/api/v1/access/applications/{applicationId}")
@RequiredArgsConstructor
public class ApplicationPermissionController {

    private final ApplicationPermissionService applicationPermissions;
    private final AccessService access;
    private final ApplicationDelegationService applicationDelegation;

    @Operation(summary = "查询应用内权限点", description = "返回应用注册的全部权限点。")
    @GetMapping("/permissions")
    @PreAuthorize("@iamAuthorization.canReadApplicationPermissions(#applicationId, authentication)")
    List<ApplicationPermissionResponse> permissions(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return applicationPermissions.listPermissions(applicationId);
    }

    @Operation(summary = "新增应用内权限点", description = "为应用新增一个权限点，编码在应用内唯一。")
    @PostMapping("/permissions")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationPermissionResponse createPermission(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "权限点定义") @Valid @RequestBody ApplicationPermissionDefinition request,
        Principal principal
    ) {
        return applicationPermissions.createPermission(applicationId, request, principal.getName());
    }

    @Operation(summary = "更新应用内权限点", description = "更新权限点名称和描述，编码不可修改。")
    @PutMapping("/permissions/{permissionId}")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    ApplicationPermissionResponse updatePermission(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "权限点 UUID") @PathVariable UUID permissionId,
        @Parameter(description = "权限点更新请求") @Valid @RequestBody UpdateApplicationPermissionRequest request,
        Principal principal
    ) {
        return applicationPermissions.updatePermission(applicationId, permissionId, request, principal.getName());
    }

    @Operation(summary = "删除应用内权限点", description = "删除权限点并从引用它的应用内角色中移除。")
    @DeleteMapping("/permissions/{permissionId}")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePermission(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "权限点 UUID") @PathVariable UUID permissionId,
        Principal principal
    ) {
        applicationPermissions.deletePermission(applicationId, permissionId, principal.getName());
    }

    @Operation(summary = "查询应用内角色", description = "返回应用内角色及其包含的权限点。")
    @GetMapping("/permission-roles")
    @PreAuthorize("@iamAuthorization.canReadApplicationPermissions(#applicationId, authentication)")
    List<ApplicationPermissionRoleResponse> roles(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return applicationPermissions.listRoles(applicationId);
    }

    @Operation(summary = "新增应用内角色", description = "创建应用内角色并选择其包含的权限点。")
    @PostMapping("/permission-roles")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationPermissionRoleResponse createRole(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色创建请求") @Valid @RequestBody CreateApplicationPermissionRoleRequest request,
        Principal principal
    ) {
        return applicationPermissions.createRole(applicationId, request, principal.getName());
    }

    @Operation(summary = "更新应用内角色", description = "更新角色名称、描述，permissionIds 不为空时整体覆盖包含的权限点。")
    @PutMapping("/permission-roles/{roleId}")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    ApplicationPermissionRoleResponse updateRole(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色 UUID") @PathVariable UUID roleId,
        @Parameter(description = "应用内角色更新请求") @Valid @RequestBody UpdateApplicationPermissionRoleRequest request,
        Principal principal
    ) {
        return applicationPermissions.updateRole(applicationId, roleId, request, principal.getName());
    }

    @Operation(summary = "删除应用内角色", description = "删除应用内角色及其全部授予记录。")
    @DeleteMapping("/permission-roles/{roleId}")
    @PreAuthorize("@iamAuthorization.canManageApplicationPermissions(#applicationId, authentication)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRole(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色 UUID") @PathVariable UUID roleId,
        Principal principal
    ) {
        applicationPermissions.deleteRole(applicationId, roleId, principal.getName());
    }

    @Operation(summary = "查询应用内角色成员", description = "返回被授予该角色的用户、用户组和组织。")
    @GetMapping("/permission-roles/{roleId}/members")
    @PreAuthorize("@iamAuthorization.canReadApplicationPermissions(#applicationId, authentication)")
    List<ApplicationPermissionRoleMemberResponse> roleMembers(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色 UUID") @PathVariable UUID roleId
    ) {
        return applicationPermissions.listRoleMembers(applicationId, roleId);
    }

    @Operation(summary = "授予应用内角色", description = "将应用内角色批量授予同一类型的用户、用户组或组织；授予组织即覆盖其下级组织成员。")
    @PostMapping("/permission-roles/{roleId}/members")
    @PreAuthorize("@iamAuthorization.canGrantApplicationRole(#applicationId, #roleId, authentication)")
    List<ApplicationPermissionRoleMemberResponse> addRoleMembers(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色 UUID") @PathVariable UUID roleId,
        @Parameter(description = "授予请求") @Valid @RequestBody AddApplicationPermissionRoleMembersRequest request,
        Principal principal
    ) {
        return applicationPermissions.addRoleMembers(applicationId, roleId, request, principal.getName());
    }

    @Operation(summary = "撤销应用内角色", description = "撤销一条应用内角色授予记录。")
    @DeleteMapping("/permission-roles/{roleId}/members/{memberId}")
    @PreAuthorize("@iamAuthorization.canGrantApplicationRole(#applicationId, #roleId, authentication)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeRoleMember(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用内角色 UUID") @PathVariable UUID roleId,
        @Parameter(description = "授予记录 UUID") @PathVariable UUID memberId,
        Authentication authentication
    ) {
        // 委派管理员不能撤销最后一名应用权限负责人；IAM 管理员不受此限制。
        boolean protectLastOwner = applicationDelegation.levelFor(applicationId, authentication) != ApplicationAdminLevel.GLOBAL;
        applicationPermissions.removeRoleMember(applicationId, roleId, memberId, authentication.getName(), protectLastOwner, null);
    }

    @Operation(summary = "当前用户的应用权限管理级别", description = "返回当前登录用户对该应用权限的管理级别，前端据此隐藏不可用的操作。")
    @GetMapping("/admin-access")
    ApplicationAdminAccessResponse adminAccess(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId, Authentication authentication) {
        return ApplicationAdminAccessResponse.of(applicationId, applicationDelegation.levelFor(applicationId, authentication));
    }

    @Operation(summary = "搜索可授予对象", description = "委派管理员授予角色时搜索用户、用户组或组织；用户只返回在职用户，应用属于租户时限定在该租户内。")
    @GetMapping("/grantable-subjects")
    @PreAuthorize("@iamAuthorization.canReadApplicationPermissions(#applicationId, authentication)")
    List<GrantableSubjectResponse> grantableSubjects(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "对象类型") @RequestParam ApplicationAssignmentSubjectType type,
        @Parameter(description = "关键字") @RequestParam(required = false) String keyword
    ) {
        return applicationDelegation.searchSubjects(applicationId, type, keyword);
    }

    @Operation(summary = "应用内权限决策", description = "先判断用户能否访问应用，再返回用户在应用内的有效权限编码，用于排查授权。")
    @GetMapping("/permission-decisions")
    @PreAuthorize("@iamAuthorization.canReadApplicationPermissions(#applicationId, authentication)")
    ApplicationPermissionDecisionResponse permissionDecision(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "用户 UUID") @RequestParam UUID userId
    ) {
        return access.decideApplicationPermissions(applicationId, userId);
    }
}
