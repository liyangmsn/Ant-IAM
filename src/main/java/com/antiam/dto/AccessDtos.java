package com.antiam.dto;

import com.antiam.domain.ApplicationProtocol;
import com.antiam.domain.ApplicationAccessRequestStatus;
import com.antiam.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AccessDtos {
    private AccessDtos() {
    }

    public record CreatePermissionRequest(
        @Schema(description = "权限编码，建议使用 resource:action 格式", example = "user:read")
        @NotBlank String code,
        @Schema(description = "权限名称", example = "查看用户")
        @NotBlank String name,
        @Schema(description = "权限描述")
        String description
    ) {
    }

    public record CreateRoleRequest(
        @Schema(description = "角色编码，租户内或系统内唯一", example = "iam_admin")
        @NotBlank String code,
        @Schema(description = "角色名称", example = "IAM 管理员")
        @NotBlank String name,
        @Schema(description = "角色描述")
        String description
    ) {
    }

    public record CreateGroupRequest(
        @Schema(description = "用户组编码", example = "engineering")
        @NotBlank String code,
        @Schema(description = "用户组名称", example = "研发部")
        @NotBlank String name,
        @Schema(description = "用户组备注")
        String description
    ) {
    }

    public record UpdatePermissionRequest(
        @Schema(description = "权限名称", example = "查看用户")
        @NotBlank String name,
        @Schema(description = "权限描述")
        String description
    ) {
    }

    public record UpdateRoleRequest(
        @Schema(description = "角色名称", example = "IAM 管理员")
        @NotBlank String name,
        @Schema(description = "角色描述")
        String description
    ) {
    }

    public record UpdateGroupRequest(
        @Schema(description = "用户组名称", example = "研发部")
        @NotBlank String name,
        @Schema(description = "用户组备注")
        String description
    ) {
    }

    public record CreateApplicationRequest(
        @Schema(description = "应用编码，系统内唯一", example = "console")
        @NotBlank String code,
        @Schema(description = "应用名称", example = "管理控制台")
        @NotBlank String name,
        @Schema(description = "应用协议类型")
        @NotNull ApplicationProtocol protocol,
        @Schema(description = "应用登录地址", example = "https://console.example.com/login")
        String loginUrl,
        @Schema(description = "所属租户 UUID；为空表示系统级应用")
        UUID tenantId,
        @Schema(description = "应用分组 UUID；为空表示未分组")
        UUID groupId
    ) {
    }

    public record UpdateApplicationRequest(
        @Schema(description = "应用名称", example = "管理控制台")
        @NotBlank String name,
        @Schema(description = "应用协议类型")
        @NotNull ApplicationProtocol protocol,
        @Schema(description = "应用登录地址", example = "https://console.example.com/login")
        String loginUrl,
        @Schema(description = "应用分组 UUID；为空表示未分组")
        UUID groupId
    ) {
    }

    public record CreateApplicationGroupRequest(
        @Schema(description = "应用分组编码", example = "standard")
        @NotBlank String code,
        @Schema(description = "应用分组名称", example = "标准应用")
        @NotBlank String name,
        @Schema(description = "应用分组备注")
        String description
    ) {
    }

    public record UpdateApplicationGroupRequest(
        @Schema(description = "应用分组名称", example = "标准应用")
        @NotBlank String name,
        @Schema(description = "应用分组备注")
        String description
    ) {
    }

    public record ConfigureApplicationSsoRequest(
        @Schema(description = "SSO 协议类型")
        @NotNull ApplicationProtocol protocol,
        @Schema(description = "OAuth/OIDC 客户端 ID", example = "console-client")
        String clientId,
        @Schema(description = "OAuth/OIDC 客户端密钥；服务端只保存哈希")
        String clientSecret,
        @Schema(description = "允许的 OAuth/OIDC 回调地址")
        Set<String> redirectUris,
        @Schema(description = "OAuth/OIDC 授权模式")
        Set<String> grantTypes,
        @Schema(description = "是否要求 PKCE")
        boolean pkceRequired,
        @Schema(description = "允许的登出回调地址")
        Set<String> postLogoutRedirectUris,
        @Schema(description = "登录发起地址")
        String loginInitiationUri,
        @Schema(description = "Access Token 有效期，单位分钟")
        Integer accessTokenTtlMinutes,
        @Schema(description = "Authorization Code 有效期，单位分钟")
        Integer authorizationCodeTtlMinutes,
        @Schema(description = "Refresh Token 有效期，单位分钟")
        Integer refreshTokenTtlMinutes,
        @Schema(description = "ID Token 有效期，单位分钟")
        Integer idTokenTtlMinutes,
        @Schema(description = "是否重用刷新令牌")
        Boolean reuseRefreshTokens,
        @Schema(description = "ID Token 签名算法")
        String idTokenSignatureAlgorithm,
        @Schema(description = "允许授权的 scope 集合")
        Set<String> scopes,
        @Schema(description = "SAML 服务提供方 Entity ID")
        String samlEntityId,
        @Schema(description = "SAML ACS 地址")
        String samlAcsUrl,
        @Schema(description = "CAS service 地址")
        String casServiceUrl,
        @Schema(description = "JWT audience")
        String jwtAudience,
        @Schema(description = "表单代填登录模板")
        String formLoginTemplate,
        @Schema(description = "ID Token 中需要包含的声明名称")
        Set<String> idTokenClaims,
        @Schema(description = "自定义声明键值")
        Map<String, String> customClaims
    ) {
    }

    public record GrantRequest(
        @Schema(description = "授权主体 UUID，例如角色、用户组或应用")
        @NotNull UUID subjectId,
        @Schema(description = "授权目标 UUID，例如权限或角色")
        @NotNull UUID targetId
    ) {
    }

    public record AddGroupMemberRequest(
        @Schema(description = "用户 UUID")
        @NotNull UUID userId
    ) {
    }

    public record PermissionResponse(UUID id, String code, String name, String description) {
    }

    public record RoleResponse(UUID id, String code, String name, String description, Set<String> permissions) {
    }

    public record RoleImpactUserResponse(
        UUID userId,
        String username,
        String displayName,
        AccountStatus status
    ) {
    }

    public record RoleImpactGroupResponse(
        UUID groupId,
        String code,
        String name,
        int memberCount
    ) {
    }

    public record RoleImpactResponse(
        UUID roleId,
        String code,
        String name,
        String description,
        Set<String> permissions,
        java.util.List<RoleImpactUserResponse> directUsers,
        java.util.List<RoleImpactGroupResponse> inheritedByGroups
    ) {
    }

    public record PermissionImpactRoleResponse(
        UUID roleId,
        String code,
        String name
    ) {
    }

    public record PermissionImpactUserResponse(
        UUID userId,
        String username,
        String displayName,
        AccountStatus status,
        Set<String> sources
    ) {
    }

    public record PermissionImpactGroupResponse(
        UUID groupId,
        String code,
        String name,
        int memberCount,
        Set<String> roles
    ) {
    }

    public record PermissionImpactResponse(
        UUID permissionId,
        String code,
        String name,
        String description,
        java.util.List<PermissionImpactRoleResponse> roles,
        java.util.List<PermissionImpactUserResponse> affectedUsers,
        java.util.List<PermissionImpactGroupResponse> affectedGroups
    ) {
    }

    public record GroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        Set<String> roles,
        int userCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record GroupMemberResponse(
        UUID userId,
        String username,
        String displayName,
        String email,
        AccountStatus status,
        UUID organizationId,
        UUID tenantId
    ) {
    }

    public record GroupEffectivePermissionResponse(
        String code,
        String name,
        String description,
        Set<String> roles
    ) {
    }

    public record GroupEffectiveAccessResponse(
        UUID groupId,
        String code,
        String name,
        Set<String> roles,
        java.util.List<GroupEffectivePermissionResponse> permissions
    ) {
    }

    public record ApplicationResponse(
        UUID id,
        String code,
        String name,
        ApplicationProtocol protocol,
        String loginUrl,
        UUID tenantId,
        UUID groupId,
        boolean enabled,
        boolean selfServiceAccessRequestEnabled
    ) {
    }

    public record ApplicationGroupResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean builtIn,
        int appCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ApplicationRoleResponse(
        UUID roleId,
        String code,
        String name,
        String description
    ) {
    }

    public record ApplicationSsoConfigResponse(
        UUID id,
        UUID applicationId,
        ApplicationProtocol protocol,
        String clientId,
        Set<String> redirectUris,
        Set<String> grantTypes,
        boolean pkceRequired,
        Set<String> postLogoutRedirectUris,
        String loginInitiationUri,
        int accessTokenTtlMinutes,
        int authorizationCodeTtlMinutes,
        int refreshTokenTtlMinutes,
        int idTokenTtlMinutes,
        boolean reuseRefreshTokens,
        String idTokenSignatureAlgorithm,
        Set<String> scopes,
        String samlEntityId,
        String samlAcsUrl,
        String casServiceUrl,
        String jwtAudience,
        Set<String> idTokenClaims,
        Map<String, String> customClaims,
        boolean enabled
    ) {
    }

    public record ApplicationAssignmentRequest(
        @Schema(description = "用户 UUID；userId 和 groupId 必须且只能填写一个")
        UUID userId,
        @Schema(description = "用户组 UUID；userId 和 groupId 必须且只能填写一个")
        UUID groupId,
        @Schema(description = "授权过期时间，ISO-8601 格式；为空表示不过期")
        Instant expiresAt
    ) {
    }

    public record CreateApplicationAccessRequest(
        @Schema(description = "申请访问的应用 UUID")
        @NotNull UUID applicationId,
        @Schema(description = "申请用户 UUID")
        @NotNull UUID userId,
        @Schema(description = "申请原因")
        String reason
    ) {
    }

    public record SelfServiceApplicationAccessRequest(
        @Schema(description = "申请访问的应用 UUID")
        @NotNull UUID applicationId,
        @Schema(description = "申请原因")
        String reason
    ) {
    }

    public record DecideApplicationAccessRequest(
        @Schema(description = "审批、拒绝或取消原因")
        String reason,
        @Schema(description = "审批通过后创建授权的过期时间，ISO-8601 格式")
        Instant assignmentExpiresAt
    ) {
    }

    public record ApplicationAssignmentResponse(
        UUID id,
        UUID applicationId,
        UUID userId,
        UUID groupId,
        Instant expiresAt,
        boolean expired,
        boolean enabled
    ) {
    }

    public record ApplicationAccessDecisionResponse(
        UUID applicationId,
        UUID userId,
        boolean allowed,
        String reason,
        UUID assignmentId
    ) {
    }

    public record UserApplicationResponse(
        UUID applicationId,
        String code,
        String name,
        ApplicationProtocol protocol,
        String loginUrl,
        UUID tenantId,
        String assignmentSource,
        UUID assignmentId,
        Instant assignmentExpiresAt
    ) {
    }

    public record RequestableApplicationResponse(
        UUID applicationId,
        String code,
        String name,
        ApplicationProtocol protocol,
        String loginUrl,
        UUID tenantId,
        boolean pendingRequest,
        UUID pendingRequestId
    ) {
    }

    public record ApplicationAccessRequestResponse(
        UUID id,
        UUID applicationId,
        UUID userId,
        ApplicationAccessRequestStatus status,
        String reason,
        String requestedBy,
        String decisionReason,
        String decidedBy,
        Instant decidedAt
    ) {
    }

    public record ApplicationAccessReviewEntryResponse(
        UUID applicationId,
        UUID userId,
        String username,
        String displayName,
        AccountStatus userStatus,
        String assignmentSource,
        UUID assignmentId,
        UUID groupId,
        String groupCode,
        Instant assignmentExpiresAt,
        boolean assignmentExpired,
        boolean assignmentEnabled
    ) {
    }
}
