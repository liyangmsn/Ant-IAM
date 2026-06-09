package com.antiam.service;

import static com.antiam.dto.ScimDtos.CreateScimGroupRequest;
import static com.antiam.dto.ScimDtos.ScimGroupResponse;
import static com.antiam.dto.ScimDtos.ScimMember;

import com.antiam.common.NotFoundException;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import java.util.List;
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
                UserAccount user = users.findById(UUID.fromString(member.value()))
                    .orElseThrow(() -> new NotFoundException("SCIM group member user not found: " + member.value()));
                user.join(saved);
            });
        }
        auditService.record(actor, "scim.group.create", "group", saved.getId().toString(), saved.getName());
        return toResponse(saved);
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
