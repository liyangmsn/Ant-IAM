package com.antiam.dto;

import com.antiam.dto.AccessDtos.ApplicationAssignmentSubjectType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApplicationPermissionDtos {

    public static final String PERMISSION_CODE_PATTERN = "^[A-Za-z0-9][A-Za-z0-9:._-]*$";

    private ApplicationPermissionDtos() {
    }

    public record ApplicationPermissionDefinition(
        @Schema(description = "权限编码，应用内唯一，可包含字母、数字以及 : . _ -", example = "order:approve")
        @NotBlank @Size(max = 160) @Pattern(regexp = PERMISSION_CODE_PATTERN) String code,
        @Schema(description = "权限名称", example = "审批订单")
        @NotBlank @Size(max = 256) String name,
        @Schema(description = "权限描述")
        @Size(max = 1024) String description
    ) {
    }

    public record UpdateApplicationPermissionRequest(
        @Schema(description = "权限名称")
        @NotBlank @Size(max = 256) String name,
        @Schema(description = "权限描述")
        @Size(max = 1024) String description
    ) {
    }

    public record ApplicationPermissionResponse(
        UUID id,
        UUID applicationId,
        String code,
        String name,
        String description,
        @Schema(description = "是否为系统保留的委派管理权限点，保留权限点不可修改、不参与同步")
        boolean reserved,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record SyncApplicationPermissionsRequest(
        @Schema(description = "应用当前声明的全部权限点；未出现在列表中的已注册权限点会被删除")
        @NotNull List<@Valid ApplicationPermissionDefinition> permissions
    ) {
    }

    public record CreateApplicationPermissionRoleRequest(
        @Schema(description = "应用内角色编码，应用内唯一", example = "order-auditor")
        @NotBlank @Size(max = 128) @Pattern(regexp = PERMISSION_CODE_PATTERN) String code,
        @Schema(description = "应用内角色名称", example = "订单审核员")
        @NotBlank @Size(max = 256) String name,
        @Schema(description = "应用内角色描述")
        @Size(max = 1024) String description,
        @Schema(description = "角色包含的应用权限点 UUID")
        List<UUID> permissionIds
    ) {
    }

    public record UpdateApplicationPermissionRoleRequest(
        @Schema(description = "应用内角色名称")
        @NotBlank @Size(max = 256) String name,
        @Schema(description = "应用内角色描述")
        @Size(max = 1024) String description,
        @Schema(description = "角色包含的应用权限点 UUID，整体覆盖")
        List<UUID> permissionIds
    ) {
    }

    public record ApplicationPermissionRoleResponse(
        UUID id,
        UUID applicationId,
        String code,
        String name,
        String description,
        @Schema(description = "是否为系统内置的委派管理角色，内置角色不可修改或删除")
        boolean builtIn,
        @Schema(description = "角色包含的权限点")
        List<ApplicationPermissionResponse> permissions,
        @Schema(description = "授予对象数量")
        long memberCount,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AddApplicationPermissionRoleMembersRequest(
        @Schema(description = "授予对象类型")
        @NotNull ApplicationAssignmentSubjectType subjectType,
        @Schema(description = "授予对象 UUID 列表")
        @NotEmpty List<UUID> subjectIds
    ) {
    }

    public record ApplicationPermissionRoleMemberResponse(
        UUID id,
        UUID roleId,
        ApplicationAssignmentSubjectType subjectType,
        UUID subjectId,
        String subjectName,
        Instant createdAt
    ) {
    }

    public record ApplicationPermissionDecisionResponse(
        UUID applicationId,
        UUID userId,
        @Schema(description = "用户是否可以访问应用；不可访问时不具备任何应用内权限")
        boolean accessAllowed,
        @Schema(description = "应用访问决策原因")
        String reason,
        @Schema(description = "用户在应用内的有效权限编码")
        List<String> permissions
    ) {
    }

    public record PermissionCheckRequest(
        @Schema(description = "用户的 access_token，必须由当前客户端签发")
        @NotBlank String token,
        @Schema(description = "待校验的权限编码", example = "[\"order:approve\"]")
        @NotEmpty List<@NotBlank String> permissions
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PermissionCheckResponse(
        @Schema(description = "token 是否有效")
        boolean active,
        @Schema(description = "是否拥有全部待校验权限")
        boolean allowed,
        @Schema(description = "用户 UUID")
        String sub,
        String username,
        @Schema(description = "拒绝原因：token_inactive、application_disabled、no_assignment、permission_denied 等")
        String reason,
        @Schema(description = "逐个权限的校验结果")
        Map<String, Boolean> results
    ) {
    }

    @Schema(description = "当前操作人对某个应用权限的管理级别")
    public enum ApplicationAdminLevel {
        /** IAM 管理员：可管理全部应用。 */
        GLOBAL,
        /** IAM 只读管理员：可查看全部应用。 */
        GLOBAL_READ,
        /** 应用权限负责人。 */
        OWNER,
        /** 授权管理员。 */
        GRANT_MANAGER,
        NONE;

        public boolean canRead() {
            return this != NONE;
        }

        public boolean canManageDefinitions() {
            return this == GLOBAL || this == OWNER;
        }

        public boolean canGrantRoles() {
            return this == GLOBAL || this == OWNER || this == GRANT_MANAGER;
        }

        public boolean canGrantDelegationRoles() {
            return this == GLOBAL || this == OWNER;
        }
    }

    public record ApplicationAdminAccessResponse(
        UUID applicationId,
        @Schema(description = "管理级别")
        ApplicationAdminLevel level,
        @Schema(description = "能否维护权限点与角色")
        boolean canManageDefinitions,
        @Schema(description = "能否授予普通角色")
        boolean canGrantRoles,
        @Schema(description = "能否授予内置管理角色")
        boolean canGrantDelegationRoles
    ) {
        public static ApplicationAdminAccessResponse of(UUID applicationId, ApplicationAdminLevel level) {
            return new ApplicationAdminAccessResponse(applicationId, level, level.canManageDefinitions(), level.canGrantRoles(), level.canGrantDelegationRoles());
        }
    }

    public record ManagedApplicationResponse(
        UUID applicationId,
        String code,
        String name,
        String description,
        @Schema(description = "当前用户对该应用的管理级别：OWNER 或 GRANT_MANAGER")
        ApplicationAdminLevel level
    ) {
    }

    public record GrantableSubjectResponse(
        ApplicationAssignmentSubjectType subjectType,
        UUID id,
        @Schema(description = "展示名称")
        String name,
        @Schema(description = "补充信息：用户为用户名与所属组织，用户组与组织为编码")
        String detail
    ) {
    }
}
