package com.antiam.web;

import static com.antiam.dto.AccessDtos.ApplicationResponse;
import static com.antiam.dto.AccessDtos.ApplicationRoleResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessReviewEntryResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessRequestResponse;
import static com.antiam.dto.AccessDtos.ApplicationAccessDecisionResponse;
import static com.antiam.dto.AccessDtos.ApplicationAssignmentRequest;
import static com.antiam.dto.AccessDtos.ApplicationAssignmentResponse;
import static com.antiam.dto.AccessDtos.ApplicationGroupResponse;
import static com.antiam.dto.AccessDtos.ApplicationSsoConfigResponse;
import static com.antiam.dto.AccessDtos.ConfigureApplicationSsoRequest;
import static com.antiam.dto.AccessDtos.AddGroupMemberRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationGroupRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.CreateApplicationRequest;
import static com.antiam.dto.AccessDtos.CreateGroupRequest;
import static com.antiam.dto.AccessDtos.CreatePermissionRequest;
import static com.antiam.dto.AccessDtos.CreateRoleRequest;
import static com.antiam.dto.AccessDtos.DecideApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.GrantRequest;
import static com.antiam.dto.AccessDtos.GroupEffectiveAccessResponse;
import static com.antiam.dto.AccessDtos.GroupMemberResponse;
import static com.antiam.dto.AccessDtos.GroupResponse;
import static com.antiam.dto.AccessDtos.PermissionImpactResponse;
import static com.antiam.dto.AccessDtos.PermissionResponse;
import static com.antiam.dto.AccessDtos.RequestableApplicationResponse;
import static com.antiam.dto.AccessDtos.RoleResponse;
import static com.antiam.dto.AccessDtos.RoleImpactResponse;
import static com.antiam.dto.AccessDtos.SelfServiceApplicationAccessRequest;
import static com.antiam.dto.AccessDtos.UpdateApplicationGroupRequest;
import static com.antiam.dto.AccessDtos.UpdateApplicationRequest;
import static com.antiam.dto.AccessDtos.UpdateGroupRequest;
import static com.antiam.dto.AccessDtos.UpdatePermissionRequest;
import static com.antiam.dto.AccessDtos.UpdateRoleRequest;
import static com.antiam.dto.AccessDtos.UserApplicationResponse;

import com.antiam.domain.ApplicationAccessRequestStatus;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
@Tag(name = "访问控制", description = "权限、角色、用户组、应用授权和访问申请管理接口")
public class AccessController {

    private final AccessService access;

    /**
     * 查询权限资源，可按编码、名称或描述关键字过滤。
     */
    @Operation(summary = "查询权限列表", description = "查询权限资源，支持 keyword 模糊匹配权限编码、名称和描述。")
    @GetMapping("/permissions")
    List<PermissionResponse> permissions(
        @Parameter(description = "关键字，匹配权限编码、名称或描述") @RequestParam(required = false) String keyword
    ) {
        return access.listPermissions(keyword);
    }

    /**
     * 创建权限资源。
     */
    @Operation(summary = "创建权限", description = "创建可授予角色的权限资源。")
    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    PermissionResponse createPermission(
        @Parameter(description = "权限创建请求") @Valid @RequestBody CreatePermissionRequest request,
        Principal principal
    ) {
        return access.createPermission(request, principal.getName());
    }

    /**
     * 查询单个权限详情。
     */
    @Operation(summary = "获取权限详情", description = "根据权限 UUID 返回权限详情。")
    @GetMapping("/permissions/{permissionId}")
    PermissionResponse permission(@Parameter(description = "权限 UUID") @PathVariable UUID permissionId) {
        return access.getPermission(permissionId);
    }

    /**
     * 更新权限名称和描述。
     */
    @Operation(summary = "更新权限", description = "更新权限名称和描述，不修改权限编码。")
    @PutMapping("/permissions/{permissionId}")
    PermissionResponse updatePermission(
        @Parameter(description = "权限 UUID") @PathVariable UUID permissionId,
        @Parameter(description = "权限更新请求") @Valid @RequestBody UpdatePermissionRequest request,
        Principal principal
    ) {
        return access.updatePermission(permissionId, request, principal.getName());
    }

    /**
     * 分析权限影响范围。
     */
    @Operation(summary = "分析权限影响", description = "返回持有该权限的角色、用户组和受影响用户。")
    @GetMapping("/permissions/{permissionId}/impact")
    PermissionImpactResponse permissionImpact(@Parameter(description = "权限 UUID") @PathVariable UUID permissionId) {
        return access.permissionImpact(permissionId);
    }

    /**
     * 查询角色资源，可按编码、名称或描述关键字过滤。
     */
    @Operation(summary = "查询角色列表", description = "查询角色资源，支持 keyword 模糊匹配角色编码、名称和描述。")
    @GetMapping("/roles")
    List<RoleResponse> roles(
        @Parameter(description = "关键字，匹配角色编码、名称或描述") @RequestParam(required = false) String keyword
    ) {
        return access.listRoles(keyword);
    }

    /**
     * 创建角色资源。
     */
    @Operation(summary = "创建角色", description = "创建可绑定权限并授予用户或用户组的角色。")
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse createRole(
        @Parameter(description = "角色创建请求") @Valid @RequestBody CreateRoleRequest request,
        Principal principal
    ) {
        return access.createRole(request, principal.getName());
    }

    /**
     * 查询单个角色详情。
     */
    @Operation(summary = "获取角色详情", description = "根据角色 UUID 返回角色详情及权限编码。")
    @GetMapping("/roles/{roleId}")
    RoleResponse role(@Parameter(description = "角色 UUID") @PathVariable UUID roleId) {
        return access.getRole(roleId);
    }

    /**
     * 更新角色名称和描述。
     */
    @Operation(summary = "更新角色", description = "更新角色名称和描述，不修改角色编码。")
    @PutMapping("/roles/{roleId}")
    RoleResponse updateRole(
        @Parameter(description = "角色 UUID") @PathVariable UUID roleId,
        @Parameter(description = "角色更新请求") @Valid @RequestBody UpdateRoleRequest request,
        Principal principal
    ) {
        return access.updateRole(roleId, request, principal.getName());
    }

    /**
     * 分析角色影响范围。
     */
    @Operation(summary = "分析角色影响", description = "返回角色包含的权限、直接授予用户和继承该角色的用户组。")
    @GetMapping("/roles/{roleId}/impact")
    RoleImpactResponse roleImpact(@Parameter(description = "角色 UUID") @PathVariable UUID roleId) {
        return access.roleImpact(roleId);
    }

    /**
     * 给角色授予权限。
     */
    @Operation(summary = "角色绑定权限", description = "通过 GrantRequest.subjectId 指定角色，targetId 指定权限。")
    @PostMapping("/role-permissions")
    RoleResponse grantPermissionToRole(
        @Parameter(description = "角色和权限绑定请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.grantPermissionToRole(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 撤销角色权限。
     */
    @Operation(summary = "角色解绑权限", description = "通过 GrantRequest.subjectId 指定角色，targetId 指定权限。")
    @DeleteMapping("/role-permissions")
    RoleResponse revokePermissionFromRole(
        @Parameter(description = "角色和权限解绑请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.revokePermissionFromRole(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 查询用户组资源，可按编码或名称关键字过滤。
     */
    @Operation(summary = "查询用户组列表", description = "查询用户组资源，支持 keyword 模糊匹配用户组编码和名称。")
    @GetMapping("/groups")
    List<GroupResponse> groups(
        @Parameter(description = "关键字，匹配用户组编码或名称") @RequestParam(required = false) String keyword
    ) {
        return access.listGroups(keyword);
    }

    /**
     * 创建用户组资源。
     */
    @Operation(summary = "创建用户组", description = "创建用户组，用于聚合成员和继承角色。")
    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse createGroup(
        @Parameter(description = "用户组创建请求") @Valid @RequestBody CreateGroupRequest request,
        Principal principal
    ) {
        return access.createGroup(request, principal.getName());
    }

    /**
     * 查询单个用户组详情。
     */
    @Operation(summary = "获取用户组详情", description = "根据用户组 UUID 返回用户组详情和角色编码。")
    @GetMapping("/groups/{groupId}")
    GroupResponse group(@Parameter(description = "用户组 UUID") @PathVariable UUID groupId) {
        return access.getGroup(groupId);
    }

    /**
     * 更新用户组名称。
     */
    @Operation(summary = "更新用户组", description = "更新用户组名称，不修改用户组编码。")
    @PutMapping("/groups/{groupId}")
    GroupResponse updateGroup(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "用户组更新请求") @Valid @RequestBody UpdateGroupRequest request,
        Principal principal
    ) {
        return access.updateGroup(groupId, request, principal.getName());
    }

    /**
     * 删除用户组。
     */
    @Operation(summary = "删除用户组", description = "删除用户组，并级联清理该组的成员关系和角色关系。")
    @DeleteMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        Principal principal
    ) {
        access.deleteGroup(groupId, principal.getName());
    }

    /**
     * 查询用户组最终生效权限。
     */
    @Operation(summary = "查询用户组有效权限", description = "聚合用户组角色，返回用户组最终生效权限。")
    @GetMapping("/groups/{groupId}/effective-access")
    GroupEffectiveAccessResponse groupEffectiveAccess(@Parameter(description = "用户组 UUID") @PathVariable UUID groupId) {
        return access.groupEffectiveAccess(groupId);
    }

    /**
     * 查询用户组成员。
     */
    @Operation(summary = "查询用户组成员", description = "返回指定用户组下的用户成员列表。")
    @GetMapping("/groups/{groupId}/members")
    List<GroupMemberResponse> groupMembers(@Parameter(description = "用户组 UUID") @PathVariable UUID groupId) {
        return access.listGroupMembers(groupId);
    }

    /**
     * 向用户组添加成员。
     */
    @Operation(summary = "添加用户组成员", description = "将指定用户加入用户组。")
    @PostMapping("/groups/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    GroupMemberResponse addGroupMember(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "用户组成员添加请求") @Valid @RequestBody AddGroupMemberRequest request,
        Principal principal
    ) {
        return access.addGroupMember(groupId, request.userId(), principal.getName());
    }

    /**
     * 从用户组移除成员。
     */
    @Operation(summary = "移除用户组成员", description = "将指定用户移出用户组。")
    @DeleteMapping("/groups/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeGroupMember(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        Principal principal
    ) {
        access.removeGroupMember(groupId, userId, principal.getName());
    }

    /**
     * 给用户组授予角色。
     */
    @Operation(summary = "用户组绑定角色", description = "通过 GrantRequest.subjectId 指定用户组，targetId 指定角色。")
    @PostMapping("/group-roles")
    GroupResponse grantRoleToGroup(
        @Parameter(description = "用户组和角色绑定请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.grantRoleToGroup(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 撤销用户组角色。
     */
    @Operation(summary = "用户组解绑角色", description = "通过 GrantRequest.subjectId 指定用户组，targetId 指定角色。")
    @DeleteMapping("/group-roles")
    GroupResponse revokeRoleFromGroup(
        @Parameter(description = "用户组和角色解绑请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.revokeRoleFromGroup(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 查询应用接入列表，可按租户、启用状态和关键字过滤。
     */
    @Operation(summary = "查询应用列表", description = "查询接入应用，支持按租户、启用状态以及应用编码、名称、登录地址关键字过滤。")
    @GetMapping("/applications")
    List<ApplicationResponse> applications(
        @Parameter(description = "租户 UUID，不传则查询全部租户应用") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "启用状态，true 仅启用应用，false 仅停用应用") @RequestParam(required = false) Boolean enabled,
        @Parameter(description = "关键字，匹配应用编码、名称或登录地址") @RequestParam(required = false) String keyword
    ) {
        return access.listApplications(tenantId, enabled, keyword);
    }

    /**
     * 创建一个新的接入应用。
     */
    @Operation(summary = "创建应用", description = "创建可配置 SSO、角色和用户授权的业务应用。")
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationResponse createApplication(
        @Parameter(description = "应用创建请求") @Valid @RequestBody CreateApplicationRequest request,
        Principal principal
    ) {
        return access.createApplication(request, principal.getName());
    }

    /**
     * 查询单个应用详情。
     */
    @Operation(summary = "获取应用详情", description = "根据应用 UUID 返回应用基础信息和租户归属。")
    @GetMapping("/applications/{applicationId}")
    ApplicationResponse application(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return access.getApplicationResponse(applicationId);
    }

    /**
     * 更新应用基础信息。
     */
    @Operation(summary = "更新应用", description = "更新应用名称、登录地址、租户和是否允许自助申请等配置。")
    @PutMapping("/applications/{applicationId}")
    ApplicationResponse updateApplication(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用更新请求") @Valid @RequestBody UpdateApplicationRequest request,
        Principal principal
    ) {
        return access.updateApplication(applicationId, request, principal.getName());
    }

    /**
     * 启用应用。
     */
    @Operation(summary = "启用应用", description = "将应用恢复为可访问状态。")
    @PostMapping("/applications/{applicationId}/enable")
    ApplicationResponse enableApplication(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId, Principal principal) {
        return access.enableApplication(applicationId, principal.getName());
    }

    /**
     * 停用应用。
     */
    @Operation(summary = "停用应用", description = "停用应用后访问决策会拒绝新的访问。")
    @PostMapping("/applications/{applicationId}/disable")
    ApplicationResponse disableApplication(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId, Principal principal) {
        return access.disableApplication(applicationId, principal.getName());
    }

    /**
     * 删除应用。
     */
    @Operation(summary = "删除应用", description = "删除应用及其协议配置、授权、访问申请和已签发凭据。")
    @DeleteMapping("/applications/{applicationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteApplication(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId, Principal principal) {
        access.deleteApplication(applicationId, principal.getName());
    }

    /**
     * 查询应用分组列表。
     */
    @Operation(summary = "查询应用分组", description = "查询控制台应用分组，支持按编码、名称或备注关键字过滤。")
    @GetMapping("/application-groups")
    List<ApplicationGroupResponse> applicationGroups(
        @Parameter(description = "关键字，匹配分组编码、名称或备注") @RequestParam(required = false) String keyword
    ) {
        return access.listApplicationGroups(keyword);
    }

    /**
     * 创建应用分组。
     */
    @Operation(summary = "创建应用分组", description = "创建用于组织应用列表的控制台应用分组。")
    @PostMapping("/application-groups")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationGroupResponse createApplicationGroup(
        @Parameter(description = "应用分组创建请求") @Valid @RequestBody CreateApplicationGroupRequest request,
        Principal principal
    ) {
        return access.createApplicationGroup(request, principal.getName());
    }

    /**
     * 更新应用分组。
     */
    @Operation(summary = "更新应用分组", description = "更新应用分组名称和备注。")
    @PutMapping("/application-groups/{groupId}")
    ApplicationGroupResponse updateApplicationGroup(
        @Parameter(description = "应用分组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "应用分组更新请求") @Valid @RequestBody UpdateApplicationGroupRequest request,
        Principal principal
    ) {
        return access.updateApplicationGroup(groupId, request, principal.getName());
    }

    /**
     * 删除应用分组。
     */
    @Operation(summary = "删除应用分组", description = "删除应用分组，分组下应用会回到未分组状态。")
    @DeleteMapping("/application-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteApplicationGroup(@Parameter(description = "应用分组 UUID") @PathVariable UUID groupId, Principal principal) {
        access.deleteApplicationGroup(groupId, principal.getName());
    }

    /**
     * 查询应用绑定的角色。
     */
    @Operation(summary = "查询应用角色", description = "返回应用可使用的角色集合，用于应用内授权映射。")
    @GetMapping("/applications/{applicationId}/roles")
    List<ApplicationRoleResponse> applicationRoles(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return access.listApplicationRoles(applicationId);
    }

    /**
     * 将角色开放给应用使用。
     */
    @Operation(summary = "应用绑定角色", description = "通过 GrantRequest.subjectId 指定应用，targetId 指定角色。")
    @PostMapping("/application-roles")
    List<ApplicationRoleResponse> grantRoleToApplication(
        @Parameter(description = "应用和角色绑定请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.grantRoleToApplication(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 从应用中移除可用角色。
     */
    @Operation(summary = "应用解绑角色", description = "通过 GrantRequest.subjectId 指定应用，targetId 指定角色。")
    @DeleteMapping("/application-roles")
    List<ApplicationRoleResponse> revokeRoleFromApplication(
        @Parameter(description = "应用和角色解绑请求") @Valid @RequestBody GrantRequest request,
        Principal principal
    ) {
        return access.revokeRoleFromApplication(request.subjectId(), request.targetId(), principal.getName());
    }

    /**
     * 查询应用 SSO 配置。
     */
    @Operation(summary = "获取应用 SSO 配置", description = "返回应用的 SSO 协议、ACS 地址、Entity ID、证书和元数据地址等配置。")
    @GetMapping("/applications/{applicationId}/sso-config")
    ApplicationSsoConfigResponse applicationSsoConfig(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return access.getApplicationSso(applicationId);
    }

    /**
     * 配置应用 SSO 参数。
     */
    @Operation(summary = "配置应用 SSO", description = "为应用配置 OIDC、SAML、CAS 等单点登录协议参数。")
    @PostMapping("/applications/{applicationId}/sso-config")
    ApplicationSsoConfigResponse configureApplicationSso(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用 SSO 配置请求") @Valid @RequestBody ConfigureApplicationSsoRequest request,
        Principal principal
    ) {
        return access.configureApplicationSso(applicationId, request, principal.getName());
    }

    /**
     * 查询应用授权记录。
     */
    @Operation(summary = "查询应用授权", description = "返回应用的用户或用户组授权记录。")
    @GetMapping("/applications/{applicationId}/assignments")
    List<ApplicationAssignmentResponse> applicationAssignments(@Parameter(description = "应用 UUID") @PathVariable UUID applicationId) {
        return access.listApplicationAssignments(applicationId);
    }

    /**
     * 对应用授权进行访问评审。
     */
    @Operation(summary = "应用访问评审", description = "汇总应用授权对象、状态、过期时间和访问来源，用于定期复核。")
    @GetMapping("/applications/{applicationId}/access-review")
    List<ApplicationAccessReviewEntryResponse> applicationAccessReview(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "是否包含已停用的授权记录") @RequestParam(defaultValue = "false") boolean includeDisabledAssignments
    ) {
        return access.reviewApplicationAccess(applicationId, includeDisabledAssignments);
    }

    /**
     * 给用户或用户组分配应用访问权。
     */
    @Operation(summary = "创建应用授权", description = "为用户或用户组授予应用访问权，可设置过期时间。")
    @PostMapping("/applications/{applicationId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationAssignmentResponse assignApplication(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用授权请求") @Valid @RequestBody ApplicationAssignmentRequest request,
        Principal principal
    ) {
        return access.assignApplication(applicationId, request, principal.getName());
    }

    /**
     * 启用应用授权记录。
     */
    @Operation(summary = "启用应用授权", description = "恢复指定应用授权记录。")
    @PostMapping("/applications/{applicationId}/assignments/{assignmentId}/enable")
    ApplicationAssignmentResponse enableApplicationAssignment(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用授权记录 UUID") @PathVariable UUID assignmentId,
        Principal principal
    ) {
        return access.enableApplicationAssignment(applicationId, assignmentId, principal.getName());
    }

    /**
     * 停用应用授权记录。
     */
    @Operation(summary = "停用应用授权", description = "停用指定应用授权记录，保留审计和历史。")
    @DeleteMapping("/applications/{applicationId}/assignments/{assignmentId}")
    ApplicationAssignmentResponse disableApplicationAssignment(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "应用授权记录 UUID") @PathVariable UUID assignmentId,
        Principal principal
    ) {
        return access.disableApplicationAssignment(applicationId, assignmentId, principal.getName());
    }

    /**
     * 判断指定用户是否可以访问应用。
     */
    @Operation(summary = "应用访问决策", description = "根据用户状态、租户、直接授权、用户组授权和授权过期时间判断是否允许访问应用。")
    @GetMapping("/applications/{applicationId}/access-decisions")
    ApplicationAccessDecisionResponse applicationAccessDecision(
        @Parameter(description = "应用 UUID") @PathVariable UUID applicationId,
        @Parameter(description = "用户 UUID") @RequestParam UUID userId
    ) {
        return access.decideApplicationAccess(applicationId, userId);
    }

    /**
     * 查询指定用户可访问的应用。
     */
    @Operation(summary = "查询用户应用门户", description = "返回指定用户当前可访问的启用应用列表。")
    @GetMapping("/users/{userId}/applications")
    List<UserApplicationResponse> userApplications(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return access.listUserApplications(userId);
    }

    /**
     * 查询当前用户可访问的应用。
     */
    @Operation(summary = "查询当前用户应用门户", description = "根据当前认证主体返回可访问的启用应用列表。")
    @GetMapping("/me/applications")
    List<UserApplicationResponse> currentUserApplications(Principal principal) {
        return access.listCurrentUserApplications(principal.getName());
    }

    /**
     * 查询当前用户可自助申请的应用。
     */
    @Operation(summary = "查询可申请应用", description = "返回当前用户尚未拥有访问权且允许自助申请的应用。")
    @GetMapping("/me/requestable-applications")
    List<RequestableApplicationResponse> currentUserRequestableApplications(Principal principal) {
        return access.listCurrentUserRequestableApplications(principal.getName());
    }

    /**
     * 当前用户提交应用访问申请。
     */
    @Operation(summary = "当前用户申请应用访问", description = "当前用户为自己提交应用访问申请。")
    @PostMapping("/me/application-access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationAccessRequestResponse requestCurrentUserApplicationAccess(
        @Parameter(description = "自助应用访问申请请求") @Valid @RequestBody SelfServiceApplicationAccessRequest request,
        Principal principal
    ) {
        return access.requestCurrentUserApplicationAccess(request, principal.getName());
    }

    /**
     * 查询当前用户的应用访问申请。
     */
    @Operation(summary = "查询当前用户应用申请", description = "返回当前用户提交的应用访问申请，可按状态过滤。")
    @GetMapping("/me/application-access-requests")
    List<ApplicationAccessRequestResponse> currentUserApplicationAccessRequests(
        @Parameter(description = "申请状态，不传则查询全部状态") @RequestParam(required = false) ApplicationAccessRequestStatus status,
        Principal principal
    ) {
        return access.listCurrentUserApplicationAccessRequests(status, principal.getName());
    }

    /**
     * 当前用户取消自己的应用访问申请。
     */
    @Operation(summary = "当前用户取消应用申请", description = "当前用户取消自己仍处于待审批状态的应用访问申请。")
    @PostMapping("/me/application-access-requests/{requestId}/cancel")
    ApplicationAccessRequestResponse cancelCurrentUserApplicationAccessRequest(
        @Parameter(description = "应用访问申请 UUID") @PathVariable UUID requestId,
        @Parameter(description = "取消原因请求，可为空") @RequestBody DecideApplicationAccessRequest request,
        Principal principal
    ) {
        return access.cancelCurrentUserApplicationAccessRequest(requestId, request, principal.getName());
    }

    /**
     * 管理员代用户提交应用访问申请。
     */
    @Operation(summary = "创建应用访问申请", description = "管理员为指定用户创建应用访问申请。")
    @PostMapping("/application-access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationAccessRequestResponse requestApplicationAccess(
        @Parameter(description = "应用访问申请请求") @Valid @RequestBody CreateApplicationAccessRequest request,
        Principal principal
    ) {
        return access.requestApplicationAccess(request, principal.getName());
    }

    /**
     * 查询应用访问申请列表。
     */
    @Operation(summary = "查询应用访问申请", description = "按状态和用户过滤应用访问申请。")
    @GetMapping("/application-access-requests")
    List<ApplicationAccessRequestResponse> applicationAccessRequests(
        @Parameter(description = "申请状态，不传则查询全部状态") @RequestParam(required = false) ApplicationAccessRequestStatus status,
        @Parameter(description = "用户 UUID，不传则查询全部用户") @RequestParam(required = false) UUID userId
    ) {
        return access.listApplicationAccessRequests(status, userId);
    }

    /**
     * 审批通过应用访问申请。
     */
    @Operation(summary = "批准应用访问申请", description = "批准待审批申请，并为申请用户创建应用授权。")
    @PostMapping("/application-access-requests/{requestId}/approve")
    ApplicationAccessRequestResponse approveApplicationAccessRequest(
        @Parameter(description = "应用访问申请 UUID") @PathVariable UUID requestId,
        @Parameter(description = "审批请求，可填写原因和授权过期时间") @RequestBody DecideApplicationAccessRequest request,
        Principal principal
    ) {
        return access.approveApplicationAccessRequest(requestId, request, principal.getName());
    }

    /**
     * 拒绝应用访问申请。
     */
    @Operation(summary = "拒绝应用访问申请", description = "拒绝待审批申请并记录审批原因。")
    @PostMapping("/application-access-requests/{requestId}/reject")
    ApplicationAccessRequestResponse rejectApplicationAccessRequest(
        @Parameter(description = "应用访问申请 UUID") @PathVariable UUID requestId,
        @Parameter(description = "审批请求，可填写拒绝原因") @RequestBody DecideApplicationAccessRequest request,
        Principal principal
    ) {
        return access.rejectApplicationAccessRequest(requestId, request, principal.getName());
    }

    /**
     * 管理员取消应用访问申请。
     */
    @Operation(summary = "取消应用访问申请", description = "管理员取消仍处于待审批状态的应用访问申请。")
    @PostMapping("/application-access-requests/{requestId}/cancel")
    ApplicationAccessRequestResponse cancelApplicationAccessRequest(
        @Parameter(description = "应用访问申请 UUID") @PathVariable UUID requestId,
        @Parameter(description = "取消原因请求，可为空") @RequestBody DecideApplicationAccessRequest request,
        Principal principal
    ) {
        return access.cancelApplicationAccessRequest(requestId, request, principal.getName());
    }
}
