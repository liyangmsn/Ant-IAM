package com.antiam.config;

import com.antiam.repository.AuthenticationSessionRepository;
import com.antiam.repository.OAuthConsentRepository;
import com.antiam.repository.PermissionRepository;
import com.antiam.repository.RoleRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 供 {@code @PreAuthorize} 使用的授权判断。
 *
 * <p>凡是会让主体获得或失去控制台权限的操作（授予 IAM 管理员角色、授予 {@code iam:*} 权限点、
 * 变更携带控制台权限的用户组成员、重置控制台用户的凭据等）只允许 IAM 管理员执行，防止模块管理员自我提权。
 */
@Component("iamAuthorization")
@RequiredArgsConstructor
public class IamAuthorizationService {

    private static final String CONSOLE_PERMISSION_PATTERN = ConsolePermission.CODE_PREFIX + "%";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final UserGroupRepository groups;
    private final PermissionRepository permissions;
    private final AuthenticationSessionRepository sessions;
    private final OAuthConsentRepository consents;

    public boolean isSelf(UUID userId, Authentication authentication) {
        return userId != null
            && authentication != null
            && users.findByUsername(authentication.getName()).map(user -> user.getId().equals(userId)).orElse(false);
    }

    public boolean isCurrentUser(UUID userId, Authentication authentication) {
        return userId == null || isSelf(userId, authentication);
    }

    public boolean isSessionOwner(UUID sessionId, Authentication authentication) {
        return sessionId != null
            && authentication != null
            && sessions.findByIdWithUser(sessionId)
                .map(session -> session.getUser() != null && authentication.getName().equals(session.getUser().getUsername()))
                .orElse(false);
    }

    public boolean isConsentOwner(UUID consentId, Authentication authentication) {
        return consentId != null
            && authentication != null
            && consents.findByIdWithUser(consentId)
                .map(consent -> consent.getUser() != null && authentication.getName().equals(consent.getUser().getUsername()))
                .orElse(false);
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return hasAuthority(authentication, SecurityAuthorities.IAM_ADMIN_AUTHORITY);
    }

    public boolean hasPermission(Authentication authentication, String permissionCode) {
        return isSuperAdmin(authentication) || hasAuthority(authentication, permissionCode);
    }

    /**
     * 查看用户的个人数据：本人，或拥有用户模块读权限。
     */
    public boolean canReadUser(UUID userId, Authentication authentication) {
        return isSelf(userId, authentication) || hasPermission(authentication, ConsolePermission.USER_READ.code());
    }

    /**
     * 修改用户的个人数据：本人；或拥有用户模块写权限且目标不是控制台用户（控制台用户只能由 IAM 管理员管理）。
     */
    public boolean canManageUser(UUID userId, Authentication authentication) {
        return isSelf(userId, authentication) || canAdministerUser(userId, authentication);
    }

    /**
     * 以管理员身份修改用户（不含本人自助场景），例如锁定、停用、重置密码。
     */
    public boolean canAdministerUser(UUID userId, Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return true;
        }
        return hasAuthority(authentication, ConsolePermission.USER_WRITE.code())
            && userId != null
            && !users.holdsConsoleAccess(userId, SecurityAuthorities.IAM_ADMIN_ROLE, CONSOLE_PERMISSION_PATTERN);
    }

    public boolean canCreateUser(String userType, Authentication authentication) {
        return isSuperAdmin(authentication)
            || (!"admin".equals(userType) && hasAuthority(authentication, ConsolePermission.USER_WRITE.code()));
    }

    /**
     * 修改角色本身或其授权关系；携带控制台权限的角色只能由 IAM 管理员修改。
     */
    public boolean canManageRole(UUID roleId, Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return true;
        }
        return hasAuthority(authentication, ConsolePermission.ROLE_WRITE.code())
            && roleId != null
            && !roles.holdsConsoleAccess(roleId, SecurityAuthorities.IAM_ADMIN_ROLE, CONSOLE_PERMISSION_PATTERN);
    }

    /**
     * 修改权限点本身或将其授予角色；控制台权限点只能由 IAM 管理员操作。
     */
    public boolean canManagePermission(UUID permissionId, Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return true;
        }
        return hasAuthority(authentication, ConsolePermission.ROLE_WRITE.code())
            && permissionId != null
            && permissions.findById(permissionId).map(permission -> !ConsolePermission.isConsoleCode(permission.getCode())).orElse(true);
    }

    public boolean canCreatePermission(String code, Authentication authentication) {
        return isSuperAdmin(authentication)
            || (!ConsolePermission.isConsoleCode(code) && hasAuthority(authentication, ConsolePermission.ROLE_WRITE.code()));
    }

    public boolean canGrantPermissionToRole(UUID roleId, UUID permissionId, Authentication authentication) {
        return canManageRole(roleId, authentication) && canManagePermission(permissionId, authentication);
    }

    /**
     * 修改用户组成员或删除用户组；携带控制台权限的用户组只能由 IAM 管理员操作。
     */
    public boolean canManageGroupMembership(UUID groupId, Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return true;
        }
        return hasAuthority(authentication, ConsolePermission.USER_WRITE.code())
            && groupId != null
            && !groups.holdsConsoleAccess(groupId, SecurityAuthorities.IAM_ADMIN_ROLE, CONSOLE_PERMISSION_PATTERN);
    }

    public boolean canReadAuthentication(UUID userId, Authentication authentication) {
        return isSelf(userId, authentication) || hasPermission(authentication, ConsolePermission.AUTHENTICATION_READ.code());
    }

    public boolean canEndSession(UUID sessionId, Authentication authentication) {
        return isSessionOwner(sessionId, authentication) || hasPermission(authentication, ConsolePermission.AUTHENTICATION_WRITE.code());
    }

    public boolean canReadConsents(UUID userId, Authentication authentication) {
        return isCurrentUser(userId, authentication) || hasPermission(authentication, ConsolePermission.APPLICATION_READ.code());
    }

    public boolean canReadConsent(UUID consentId, Authentication authentication) {
        return isConsentOwner(consentId, authentication) || hasPermission(authentication, ConsolePermission.APPLICATION_READ.code());
    }

    public boolean canRevokeConsent(UUID consentId, Authentication authentication) {
        return isConsentOwner(consentId, authentication) || hasPermission(authentication, ConsolePermission.APPLICATION_WRITE.code());
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
