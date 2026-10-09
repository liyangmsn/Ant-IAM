package com.antiam.service;

import com.antiam.config.ConsolePermission;
import com.antiam.config.SecurityAuthorities;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationDelegation;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.dto.AccessDtos.ApplicationAssignmentSubjectType;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationAdminLevel;
import com.antiam.dto.ApplicationPermissionDtos.GrantableSubjectResponse;
import com.antiam.dto.ApplicationPermissionDtos.ManagedApplicationResponse;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用权限委派：判断当前操作人对某个应用权限的管理级别，并提供委派管理员需要的辅助查询。
 *
 * <p>委派身份来自应用内的保留权限点，经由应用访问决策计算，所以人员停用、离职或失去应用访问授权时，管理能力同步失效。
 */
@Service
@RequiredArgsConstructor
public class ApplicationDelegationService {

    private static final int SUBJECT_SEARCH_LIMIT = 20;

    private final AccessService access;
    private final ApplicationPermissionService applicationPermissions;
    private final ApplicationRepository applications;
    private final UserAccountRepository users;
    private final UserGroupRepository groups;
    private final OrganizationRepository organizations;

    /**
     * 控制台与门户使用：IAM 管理员（应用模块写权限）为 GLOBAL；否则看委派身份；只读管理员兜底为 GLOBAL_READ。
     */
    @Transactional(readOnly = true)
    public ApplicationAdminLevel levelFor(UUID applicationId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || applicationId == null) {
            return ApplicationAdminLevel.NONE;
        }
        if (hasAuthority(authentication, SecurityAuthorities.IAM_ADMIN_AUTHORITY)
            || hasAuthority(authentication, ConsolePermission.APPLICATION_WRITE.code())) {
            return ApplicationAdminLevel.GLOBAL;
        }
        ApplicationAdminLevel delegated = users.findByUsername(authentication.getName())
            .map(user -> userLevel(applicationId, user.getId()))
            .orElse(ApplicationAdminLevel.NONE);
        if (delegated != ApplicationAdminLevel.NONE) {
            return delegated;
        }
        return hasAuthority(authentication, ConsolePermission.APPLICATION_READ.code())
            ? ApplicationAdminLevel.GLOBAL_READ
            : ApplicationAdminLevel.NONE;
    }

    /** 按用户在应用内的有效权限判断委派身份；用户不能访问应用时一律为 NONE。 */
    @Transactional(readOnly = true)
    public ApplicationAdminLevel userLevel(UUID applicationId, UUID userId) {
        List<String> permissions = access.decideApplicationPermissions(applicationId, userId).permissions();
        if (permissions.contains(ApplicationDelegation.PERMISSION_MANAGE)) {
            return ApplicationAdminLevel.OWNER;
        }
        if (permissions.contains(ApplicationDelegation.GRANT_MANAGE)) {
            return ApplicationAdminLevel.GRANT_MANAGER;
        }
        return ApplicationAdminLevel.NONE;
    }

    /** 能否授予或撤销某个角色：内置管理角色只有 IAM 管理员和应用权限负责人能授予。 */
    @Transactional(readOnly = true)
    public boolean canGrantRole(ApplicationAdminLevel level, UUID applicationId, UUID roleId) {
        if (!level.canGrantRoles()) {
            return false;
        }
        return level.canGrantDelegationRoles() || !applicationPermissions.roleGrantsDelegation(applicationId, roleId);
    }

    /** 门户「我管理的应用」：当前用户以负责人或授权管理员身份管理的应用。 */
    @Transactional(readOnly = true)
    public List<ManagedApplicationResponse> managedApplications(String username) {
        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null) {
            return List.of();
        }
        Set<UUID> groupIds = user.getGroups().stream().map(UserGroup::getId).collect(Collectors.toSet());
        Set<UUID> candidates = applicationPermissions.delegatedApplicationIds(user.getId(), groupIds, organizationChain(user));
        return applications.findAllById(candidates).stream()
            .map(application -> new ManagedApplicationResponse(
                application.getId(),
                application.getCode(),
                application.getName(),
                application.getDescription(),
                userLevel(application.getId(), user.getId())))
            .filter(managed -> managed.level() != ApplicationAdminLevel.NONE)
            .sorted(java.util.Comparator.comparing(ManagedApplicationResponse::code))
            .toList();
    }

    /**
     * 委派管理员选择授予对象：只返回在职用户（应用属于租户时限定在该租户），以及用户组、组织；每类最多 20 条，只给最少字段。
     */
    @Transactional(readOnly = true)
    public List<GrantableSubjectResponse> searchSubjects(UUID applicationId, ApplicationAssignmentSubjectType type, String keyword) {
        Application application = applications.findById(applicationId)
            .orElseThrow(() -> new com.antiam.common.NotFoundException("Application not found: " + applicationId));
        String term = keyword == null ? "" : keyword.trim();
        return switch (type) {
            case USER -> {
                String pattern = "%" + term.toLowerCase() + "%";
                PageRequest page = PageRequest.of(0, SUBJECT_SEARCH_LIMIT);
                List<UserAccount> found = application.getTenant() == null
                    ? users.searchActive(pattern, page)
                    : users.searchActiveInTenant(application.getTenant().getId(), pattern, page);
                yield found.stream().map(ApplicationDelegationService::toSubject).toList();
            }
            case GROUP -> groups.findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(term, term).stream()
                .map(group -> new GrantableSubjectResponse(ApplicationAssignmentSubjectType.GROUP, group.getId(), group.getName(), group.getCode()))
                .toList();
            case ORGANIZATION -> organizations.findTop20ByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(term, term).stream()
                .map(organization -> new GrantableSubjectResponse(ApplicationAssignmentSubjectType.ORGANIZATION, organization.getId(), organization.getName(), organization.getCode()))
                .toList();
        };
    }

    private static GrantableSubjectResponse toSubject(UserAccount user) {
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank() ? user.getUsername() : user.getDisplayName();
        String detail = user.getOrganization() == null ? user.getUsername() : user.getUsername() + " · " + user.getOrganization().getName();
        return new GrantableSubjectResponse(ApplicationAssignmentSubjectType.USER, user.getId(), name, detail);
    }

    // 用户所属组织及其全部上级组织；授予上级组织即覆盖下级组织成员。
    private static Set<UUID> organizationChain(UserAccount user) {
        Set<UUID> ids = new LinkedHashSet<>();
        Organization current = user.getOrganization();
        while (current != null && ids.add(current.getId())) {
            current = current.getParent();
        }
        return ids;
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
