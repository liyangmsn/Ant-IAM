package com.antiam.web;

import com.antiam.common.ForbiddenException;
import com.antiam.dto.AccessDtos.ApplicationAssignmentSubjectType;
import com.antiam.dto.ApplicationPermissionDtos.AddApplicationPermissionRoleMembersRequest;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminAccessResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminLevel;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDecisionResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleMemberResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleResponse;
import com.antiam.dto.ApplicationPermissionDtos.GrantableSubjectResponse;
import com.antiam.service.AccessService;
import com.antiam.service.ApplicationDelegationService;
import com.antiam.service.ApplicationPermissionService;
import com.antiam.service.OAuthService;
import com.antiam.service.OAuthService.ActingUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务应用代表用户管理自身应用权限的接口。
 *
 * <p>每个请求同时携带客户端凭据（确定是哪个应用）和操作人的 access_token（{@code X-Acting-User-Token}，确定是谁在操作），
 * IAM 按操作人在该应用内的委派身份判定能否执行；不接受只有客户端凭据的管理请求。角色用编码寻址。
 */
@Tag(name = "应用权限委派管理", description = "业务应用代表委派管理员管理本应用的角色授予")
@RestController
@RequestMapping("/oauth2/permission-admin")
@RequiredArgsConstructor
public class ClientPermissionAdminController {

    private static final String ACTING_USER_HEADER = "X-Acting-User-Token";

    private final OAuthService oauth;
    private final AccessService access;
    private final ApplicationPermissionService applicationPermissions;
    private final ApplicationDelegationService applicationDelegation;

    @Operation(summary = "操作人的管理级别", description = "返回操作人对本应用权限的管理级别，业务应用据此决定是否展示权限管理入口。")
    @GetMapping("/me")
    ApplicationAdminAccessResponse me(
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = caller(authorization, clientId, clientSecret, actingUserToken);
        return ApplicationAdminAccessResponse.of(caller.user().applicationId(), caller.level());
    }

    @Operation(summary = "本应用的角色", description = "返回本应用的应用内角色及其权限点。")
    @GetMapping("/roles")
    List<ApplicationPermissionRoleResponse> roles(
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = requireRead(caller(authorization, clientId, clientSecret, actingUserToken));
        return applicationPermissions.listRoles(caller.user().applicationId());
    }

    @Operation(summary = "角色的授予对象", description = "按角色编码查询被授予该角色的用户、用户组和组织。")
    @GetMapping("/roles/{roleCode}/members")
    List<ApplicationPermissionRoleMemberResponse> members(
        @Parameter(description = "应用内角色编码") @PathVariable String roleCode,
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = requireRead(caller(authorization, clientId, clientSecret, actingUserToken));
        UUID applicationId = caller.user().applicationId();
        return applicationPermissions.listRoleMembers(applicationId, applicationPermissions.roleIdByCode(applicationId, roleCode));
    }

    @Operation(summary = "授予角色", description = "按角色编码把角色授予用户、用户组或组织；授权管理员不能授予内置管理角色。")
    @PostMapping("/roles/{roleCode}/members")
    List<ApplicationPermissionRoleMemberResponse> addMembers(
        @Parameter(description = "应用内角色编码") @PathVariable String roleCode,
        @Valid @RequestBody AddApplicationPermissionRoleMembersRequest request,
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = caller(authorization, clientId, clientSecret, actingUserToken);
        UUID applicationId = caller.user().applicationId();
        UUID roleId = requireGrant(caller, roleCode);
        return applicationPermissions.addRoleMembers(applicationId, roleId, request, caller.user().username(), caller.via());
    }

    @Operation(summary = "撤销角色", description = "撤销一条角色授予记录；不能撤销最后一名应用权限负责人。")
    @DeleteMapping("/roles/{roleCode}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(
        @Parameter(description = "应用内角色编码") @PathVariable String roleCode,
        @Parameter(description = "授予记录 UUID") @PathVariable UUID memberId,
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = caller(authorization, clientId, clientSecret, actingUserToken);
        UUID roleId = requireGrant(caller, roleCode);
        applicationPermissions.removeRoleMember(caller.user().applicationId(), roleId, memberId, caller.user().username(), true, caller.via());
    }

    @Operation(summary = "搜索可授予对象", description = "搜索在职用户、用户组或组织，只返回 ID、名称和少量补充信息。")
    @GetMapping("/subjects")
    List<GrantableSubjectResponse> subjects(
        @Parameter(description = "对象类型") @RequestParam ApplicationAssignmentSubjectType type,
        @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = caller(authorization, clientId, clientSecret, actingUserToken);
        if (!caller.level().canGrantRoles()) {
            throw new ForbiddenException("操作人不是本应用的委派管理员");
        }
        return applicationDelegation.searchSubjects(caller.user().applicationId(), type, keyword);
    }

    @Operation(summary = "某人在本应用的有效权限", description = "用于业务应用的权限管理页展示某个用户当前拥有的权限。")
    @GetMapping("/users/{userId}/permissions")
    ApplicationPermissionDecisionResponse userPermissions(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @RequestHeader(value = ACTING_USER_HEADER, required = false) String actingUserToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(value = "client_id", required = false) String clientId,
        @RequestParam(value = "client_secret", required = false) String clientSecret
    ) {
        Caller caller = requireRead(caller(authorization, clientId, clientSecret, actingUserToken));
        return access.decideApplicationPermissions(caller.user().applicationId(), userId);
    }

    private Caller caller(String authorization, String clientId, String clientSecret, String actingUserToken) {
        ClientCredentials client = ClientCredentials.parse(authorization, clientId, clientSecret);
        ActingUser user = oauth.resolveActingUser(client.id(), client.secret(), actingUserToken);
        return new Caller(user, applicationDelegation.userLevel(user.applicationId(), user.userId()), "client:" + client.id());
    }

    private static Caller requireRead(Caller caller) {
        if (!caller.level().canRead()) {
            throw new ForbiddenException("操作人不是本应用的委派管理员");
        }
        return caller;
    }

    private UUID requireGrant(Caller caller, String roleCode) {
        UUID applicationId = caller.user().applicationId();
        UUID roleId = applicationPermissions.roleIdByCode(applicationId, roleCode);
        if (!applicationDelegation.canGrantRole(caller.level(), applicationId, roleId)) {
            throw new ForbiddenException(caller.level().canGrantRoles()
                ? "授权管理员不能授予或撤销内置管理角色：" + roleCode
                : "操作人不是本应用的委派管理员");
        }
        return roleId;
    }

    private record Caller(ActingUser user, ApplicationAdminLevel level, String via) {
    }
}
