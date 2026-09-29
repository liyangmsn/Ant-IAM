package com.antiam.service;

import static com.antiam.dto.ScimDtos.CreateScimGroupRequest;
import static com.antiam.dto.ScimDtos.ScimGroupResponse;
import static com.antiam.dto.ScimDtos.ScimMember;

import com.antiam.common.NotFoundException;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScimGroupService {

    private static final List<String> GROUP_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:Group");

    private final UserGroupRepository groups;
    private final UserAccountRepository users;
    private final AuditService auditService;

    /**
     * 返回所有内部用户组的 SCIM Group 视图，用于外部系统全量同步。
     */
    @Transactional(readOnly = true)
    public List<ScimGroupResponse> list() {
        return groups.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * 按用户组 ID 返回单个 SCIM Group 资源，供外部系统做同步校验。
     */
    @Transactional(readOnly = true)
    public ScimGroupResponse get(UUID groupId) {
        return groups.findById(groupId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("SCIM group not found: " + groupId));
    }

    /**
     * 创建 SCIM Group，并将请求中的成员 ID 绑定到新建用户组。
     */
    @Transactional
    public ScimGroupResponse create(CreateScimGroupRequest request, String actor) {
        UserGroup saved = groups.save(new UserGroup(nextCode(), request.displayName()));
        if (request.members() != null) {
            request.members().forEach(member -> {
                findMember(memberId(member.value())).join(saved);
            });
        }
        auditService.record(actor, "scim.group.create", "group", saved.getId().toString(), saved.getName());
        return toResponse(saved);
    }

    /**
     * 重命名 SCIM Group，并按目标成员列表增删成员；memberIds 为 null 时保持成员不变。
     */
    @Transactional
    public ScimGroupResponse update(UUID groupId, String displayName, List<String> memberIds, String actor) {
        UserGroup group = getEntity(groupId);
        if (displayName != null && !displayName.isBlank() && !displayName.equals(group.getName())) {
            group.rename(displayName.trim());
        }
        if (memberIds != null) {
            Set<UUID> target = new LinkedHashSet<>();
            memberIds.forEach(id -> target.add(memberId(id)));
            List<UserAccount> current = users.findByGroupsId(groupId);
            current.stream()
                .filter(user -> !target.contains(user.getId()))
                .forEach(user -> user.leave(group));
            Set<UUID> existing = new LinkedHashSet<>();
            current.forEach(user -> existing.add(user.getId()));
            target.stream()
                .filter(id -> !existing.contains(id))
                .forEach(id -> findMember(id).join(group));
        }
        auditService.record(actor, "scim.group.update", "group", groupId.toString(), group.getName());
        return toResponse(group);
    }

    /**
     * 返回用户组当前成员 ID，供 PATCH 在现有成员基础上增删。
     */
    @Transactional(readOnly = true)
    public List<String> memberIds(UUID groupId) {
        getEntity(groupId);
        return users.findByGroupsId(groupId).stream().map(user -> user.getId().toString()).toList();
    }

    /**
     * 删除 SCIM Group，同时解除成员关系。
     */
    @Transactional
    public void delete(UUID groupId, String actor) {
        UserGroup group = getEntity(groupId);
        users.findByGroupsId(groupId).forEach(user -> user.leave(group));
        String name = group.getName();
        groups.delete(group);
        auditService.record(actor, "scim.group.delete", "group", groupId.toString(), name);
    }

    private UserGroup getEntity(UUID groupId) {
        return groups.findById(groupId)
            .orElseThrow(() -> new NotFoundException("SCIM group not found: " + groupId));
    }

    private UUID memberId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("SCIM group member value must be a user id: " + value);
        }
    }

    private UserAccount findMember(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new NotFoundException("SCIM group member user not found: " + userId));
    }

    private ScimGroupResponse toResponse(UserGroup group) {
        List<ScimMember> members = users.findByGroupsId(group.getId()).stream()
            .map(user -> new ScimMember(
                user.getId().toString(),
                user.getDisplayName(),
                "/scim/v2/Users/" + user.getId(),
                "User"))
            .toList();
        return new ScimGroupResponse(GROUP_SCHEMA, group.getId().toString(), group.getName(), members);
    }

    private String nextCode() {
        return "scim-" + UUID.randomUUID();
    }
}
