package com.antiam.service;

import com.antiam.common.ConflictException;
import com.antiam.common.NotFoundException;
import com.antiam.domain.Application;
import com.antiam.domain.ApplicationPermission;
import com.antiam.domain.ApplicationPermissionRole;
import com.antiam.domain.ApplicationPermissionRoleMember;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.dto.AccessDtos.ApplicationAssignmentSubjectType;
import com.antiam.dto.ApplicationPermissionDtos.AddApplicationPermissionRoleMembersRequest;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionDefinition;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleMemberResponse;
import com.antiam.dto.ApplicationPermissionDtos.ApplicationPermissionRoleResponse;
import com.antiam.dto.ApplicationPermissionDtos.CreateApplicationPermissionRoleRequest;
import com.antiam.dto.ApplicationPermissionDtos.UpdateApplicationPermissionRequest;
import com.antiam.dto.ApplicationPermissionDtos.UpdateApplicationPermissionRoleRequest;
import com.antiam.repository.ApplicationPermissionRepository;
import com.antiam.repository.ApplicationPermissionRoleMemberRepository;
import com.antiam.repository.ApplicationPermissionRoleRepository;
import com.antiam.repository.ApplicationRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用内权限管理：应用注册时声明的权限点、聚合权限点的应用内角色，以及角色的授予对象。
 */
@Service
@RequiredArgsConstructor
public class ApplicationPermissionService {

    private final ApplicationRepository applications;
    private final ApplicationPermissionRepository permissions;
    private final ApplicationPermissionRoleRepository roles;
    private final ApplicationPermissionRoleMemberRepository members;
    private final UserAccountRepository users;
    private final UserGroupRepository groups;
    private final OrganizationRepository organizations;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ApplicationPermissionResponse> listPermissions(UUID applicationId) {
        getApplication(applicationId);
        return permissions.findByApplicationIdOrderByCodeAsc(applicationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    // 在控制台为应用新增单个权限点。
    public ApplicationPermissionResponse createPermission(UUID applicationId, ApplicationPermissionDefinition request, String actor) {
        Application application = getApplication(applicationId);
        if (permissions.existsByApplicationIdAndCode(applicationId, request.code())) {
            throw new ConflictException("Permission code already exists in application: " + request.code());
        }
        ApplicationPermission saved = permissions.save(new ApplicationPermission(application, request.code(), request.name(), request.description()));
        auditService.record(actor, "application.permission.create", "application", applicationId.toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public ApplicationPermissionResponse updatePermission(UUID applicationId, UUID permissionId, UpdateApplicationPermissionRequest request, String actor) {
        ApplicationPermission permission = getPermission(applicationId, permissionId);
        permission.update(request.name(), request.description());
        auditService.record(actor, "application.permission.update", "application", applicationId.toString(), permission.getCode());
        return toResponse(permission);
    }

    @Transactional
    // 删除权限点，并从引用它的应用内角色中移除。
    public void deletePermission(UUID applicationId, UUID permissionId, String actor) {
        ApplicationPermission permission = getPermission(applicationId, permissionId);
        removePermission(permission);
        auditService.record(actor, "application.permission.delete", "application", applicationId.toString(), permission.getCode());
    }

    /**
     * 按应用声明的清单整体同步权限点：新增缺失的、更新名称和描述、删除清单外的。
     * 供创建应用和应用以客户端凭据自助注册使用。
     */
    @Transactional
    public List<ApplicationPermissionResponse> syncPermissions(Application application, Collection<ApplicationPermissionDefinition> definitions, String actor) {
        Map<String, ApplicationPermissionDefinition> declared = indexByCode(definitions);
        Map<String, ApplicationPermission> existing = permissions.findByApplicationIdOrderByCodeAsc(application.getId()).stream()
            .collect(Collectors.toMap(ApplicationPermission::getCode, Function.identity()));
        int created = 0;
        int updated = 0;
        int deleted = 0;
        for (ApplicationPermissionDefinition definition : declared.values()) {
            ApplicationPermission current = existing.get(definition.code());
            if (current == null) {
                permissions.save(new ApplicationPermission(application, definition.code(), definition.name(), definition.description()));
                created++;
            } else if (!definition.name().equals(current.getName()) || !java.util.Objects.equals(definition.description(), current.getDescription())) {
                current.update(definition.name(), definition.description());
                updated++;
            }
        }
        for (ApplicationPermission current : existing.values()) {
            if (!declared.containsKey(current.getCode())) {
                removePermission(current);
                deleted++;
            }
        }
        auditService.record(actor, "application.permission.sync", "application", application.getId().toString(),
            "created=" + created + ";updated=" + updated + ";deleted=" + deleted);
        return permissions.findByApplicationIdOrderByCodeAsc(application.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationPermissionRoleResponse> listRoles(UUID applicationId) {
        getApplication(applicationId);
        return roles.findByApplicationIdOrderByCodeAsc(applicationId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApplicationPermissionRoleResponse createRole(UUID applicationId, CreateApplicationPermissionRoleRequest request, String actor) {
        Application application = getApplication(applicationId);
        if (roles.existsByApplicationIdAndCode(applicationId, request.code())) {
            throw new ConflictException("Role code already exists in application: " + request.code());
        }
        ApplicationPermissionRole role = new ApplicationPermissionRole(application, request.code(), request.name(), request.description());
        role.replacePermissions(resolvePermissions(applicationId, request.permissionIds()));
        ApplicationPermissionRole saved = roles.save(role);
        auditService.record(actor, "application.permission_role.create", "application", applicationId.toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    public ApplicationPermissionRoleResponse updateRole(UUID applicationId, UUID roleId, UpdateApplicationPermissionRoleRequest request, String actor) {
        ApplicationPermissionRole role = getRole(applicationId, roleId);
        role.update(request.name(), request.description());
        if (request.permissionIds() != null) {
            role.replacePermissions(resolvePermissions(applicationId, request.permissionIds()));
        }
        auditService.record(actor, "application.permission_role.update", "application", applicationId.toString(), role.getCode());
        return toResponse(role);
    }

    @Transactional
    public void deleteRole(UUID applicationId, UUID roleId, String actor) {
        ApplicationPermissionRole role = getRole(applicationId, roleId);
        members.deleteAll(members.findByRoleIdOrderByCreatedAtAsc(roleId));
        role.replacePermissions(List.of());
        roles.delete(role);
        auditService.record(actor, "application.permission_role.delete", "application", applicationId.toString(), role.getCode());
    }

    @Transactional(readOnly = true)
    public List<ApplicationPermissionRoleMemberResponse> listRoleMembers(UUID applicationId, UUID roleId) {
        getRole(applicationId, roleId);
        return members.findByRoleIdOrderByCreatedAtAsc(roleId).stream().map(this::toResponse).toList();
    }

    @Transactional
    // 将应用内角色授予用户、用户组或组织，已授予的对象会被忽略。
    public List<ApplicationPermissionRoleMemberResponse> addRoleMembers(
        UUID applicationId,
        UUID roleId,
        AddApplicationPermissionRoleMembersRequest request,
        String actor
    ) {
        ApplicationPermissionRole role = getRole(applicationId, roleId);
        for (UUID subjectId : new java.util.LinkedHashSet<>(request.subjectIds())) {
            ApplicationPermissionRoleMember member = switch (request.subjectType()) {
                case USER -> members.existsByRoleIdAndUserId(roleId, subjectId) ? null
                    : ApplicationPermissionRoleMember.ofUser(role, users.findById(subjectId)
                        .orElseThrow(() -> new NotFoundException("User not found: " + subjectId)));
                case GROUP -> members.existsByRoleIdAndGroupId(roleId, subjectId) ? null
                    : ApplicationPermissionRoleMember.ofGroup(role, groups.findById(subjectId)
                        .orElseThrow(() -> new NotFoundException("User group not found: " + subjectId)));
                case ORGANIZATION -> members.existsByRoleIdAndOrganizationId(roleId, subjectId) ? null
                    : ApplicationPermissionRoleMember.ofOrganization(role, organizations.findById(subjectId)
                        .orElseThrow(() -> new NotFoundException("Organization not found: " + subjectId)));
            };
            if (member != null) {
                members.save(member);
                auditService.record(actor, "application.permission_role.grant", "application", applicationId.toString(),
                    role.getCode() + ";" + request.subjectType() + "=" + subjectId);
            }
        }
        return listRoleMembers(applicationId, roleId);
    }

    @Transactional
    public void removeRoleMember(UUID applicationId, UUID roleId, UUID memberId, String actor) {
        ApplicationPermissionRole role = getRole(applicationId, roleId);
        ApplicationPermissionRoleMember member = members.findByIdAndRoleId(memberId, roleId)
            .orElseThrow(() -> new NotFoundException("Role member not found: " + memberId));
        members.delete(member);
        auditService.record(actor, "application.permission_role.revoke", "application", applicationId.toString(),
            role.getCode() + ";" + subjectType(member) + "=" + subjectId(member));
    }

    /**
     * 计算用户在应用内的有效权限：直接授予用户、授予其所在用户组、授予其所属组织或上级组织的应用内角色所含权限点的并集。
     * 调用方负责先完成应用访问决策。
     */
    @Transactional(readOnly = true)
    public Set<String> effectivePermissionCodes(UUID applicationId, UUID userId, Set<UUID> groupIds, Set<UUID> organizationIds) {
        Set<String> codes = new TreeSet<>();
        for (ApplicationPermissionRoleMember member : members.findByApplicationIdWithPermissions(applicationId)) {
            boolean matched = (member.getUser() != null && member.getUser().getId().equals(userId))
                || (member.getGroup() != null && groupIds.contains(member.getGroup().getId()))
                || (member.getOrganization() != null && organizationIds.contains(member.getOrganization().getId()));
            if (matched) {
                member.getRole().getPermissions().forEach(permission -> codes.add(permission.getCode()));
            }
        }
        return codes;
    }

    private void removePermission(ApplicationPermission permission) {
        roles.findByPermissionsId(permission.getId()).forEach(role -> role.revoke(permission));
        permissions.delete(permission);
    }

    private Map<String, ApplicationPermissionDefinition> indexByCode(Collection<ApplicationPermissionDefinition> definitions) {
        Map<String, ApplicationPermissionDefinition> indexed = new LinkedHashMap<>();
        if (definitions == null) {
            return indexed;
        }
        for (ApplicationPermissionDefinition definition : definitions) {
            if (indexed.put(definition.code(), definition) != null) {
                throw new IllegalArgumentException("Duplicate permission code: " + definition.code());
            }
        }
        return indexed;
    }

    private List<ApplicationPermission> resolvePermissions(UUID applicationId, List<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = new HashSet<>(permissionIds);
        List<ApplicationPermission> resolved = permissions.findAllById(ids).stream()
            .filter(permission -> permission.getApplication().getId().equals(applicationId))
            .toList();
        if (resolved.size() != ids.size()) {
            throw new IllegalArgumentException("Permission does not belong to application: " + applicationId);
        }
        return resolved;
    }

    private Application getApplication(UUID applicationId) {
        return applications.findById(applicationId)
            .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));
    }

    private ApplicationPermission getPermission(UUID applicationId, UUID permissionId) {
        return permissions.findByIdAndApplicationId(permissionId, applicationId)
            .orElseThrow(() -> new NotFoundException("Application permission not found: " + permissionId));
    }

    private ApplicationPermissionRole getRole(UUID applicationId, UUID roleId) {
        return roles.findByIdAndApplicationId(roleId, applicationId)
            .orElseThrow(() -> new NotFoundException("Application role not found: " + roleId));
    }

    private ApplicationPermissionResponse toResponse(ApplicationPermission permission) {
        return new ApplicationPermissionResponse(
            permission.getId(),
            permission.getApplication().getId(),
            permission.getCode(),
            permission.getName(),
            permission.getDescription(),
            permission.getCreatedAt(),
            permission.getUpdatedAt());
    }

    private ApplicationPermissionRoleResponse toResponse(ApplicationPermissionRole role) {
        return new ApplicationPermissionRoleResponse(
            role.getId(),
            role.getApplication().getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.getPermissions().stream()
                .sorted(java.util.Comparator.comparing(ApplicationPermission::getCode))
                .map(this::toResponse)
                .toList(),
            role.getId() == null ? 0 : members.countByRoleId(role.getId()),
            role.getCreatedAt(),
            role.getUpdatedAt());
    }

    private ApplicationPermissionRoleMemberResponse toResponse(ApplicationPermissionRoleMember member) {
        return new ApplicationPermissionRoleMemberResponse(
            member.getId(),
            member.getRole().getId(),
            subjectType(member),
            subjectId(member),
            subjectName(member),
            member.getCreatedAt());
    }

    private static ApplicationAssignmentSubjectType subjectType(ApplicationPermissionRoleMember member) {
        if (member.getUser() != null) {
            return ApplicationAssignmentSubjectType.USER;
        }
        return member.getOrganization() != null ? ApplicationAssignmentSubjectType.ORGANIZATION : ApplicationAssignmentSubjectType.GROUP;
    }

    private static UUID subjectId(ApplicationPermissionRoleMember member) {
        if (member.getUser() != null) {
            return member.getUser().getId();
        }
        return member.getOrganization() != null ? member.getOrganization().getId() : member.getGroup().getId();
    }

    private static String subjectName(ApplicationPermissionRoleMember member) {
        if (member.getUser() != null) {
            UserAccount user = member.getUser();
            return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername()
                : user.getDisplayName() + "（" + user.getUsername() + "）";
        }
        Organization organization = member.getOrganization();
        if (organization != null) {
            return organization.getName();
        }
        UserGroup group = member.getGroup();
        return group.getName();
    }
}
