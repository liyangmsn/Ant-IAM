package com.antiam.service;

import static com.antiam.dto.OrganizationDtos.CreateOrganizationRequest;
import static com.antiam.dto.OrganizationDtos.OrganizationResponse;
import static com.antiam.dto.OrganizationDtos.OrganizationTreeResponse;
import static com.antiam.dto.OrganizationDtos.OrganizationUserResponse;
import static com.antiam.dto.OrganizationDtos.UpdateOrganizationRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.mapper.OrganizationMapper;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final OrganizationMapper organizationMapper;
    private final AuditService auditService;

    /**
     * 创建组织节点，支持挂载到指定父组织下。
     */
    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request, String actor) {
        Organization parent = request.parentId() == null ? null : getEntity(request.parentId());
        Organization saved = organizations.save(new Organization(request.code(), request.name(), parent));
        auditService.record(actor, "organization.create", "organization", saved.getId().toString(), saved.getCode());
        return organizationMapper.toResponse(saved);
    }

    /**
     * 更新组织名称和父级关系，并校验不会形成循环层级。
     */
    @Transactional
    public OrganizationResponse update(UUID organizationId, UpdateOrganizationRequest request, String actor) {
        Organization organization = getEntity(organizationId);
        Organization parent = request.parentId() == null ? null : getEntity(request.parentId());
        validateParent(organization, parent);
        organization.update(request.name(), parent);
        auditService.record(actor, "organization.update", "organization", organizationId.toString(), organization.getCode());
        return organizationMapper.toResponse(organization);
    }

    /**
     * 通过 SCIM 创建组织节点，复用内部组织目录模型。
     */
    @Transactional
    public OrganizationResponse createScimOrganization(String code, String name, UUID parentId, String actor) {
        Organization parent = parentId == null ? null : getEntity(parentId);
        Organization saved = organizations.save(new Organization(code, name, parent));
        auditService.record(actor, "scim.organization.create", "organization", saved.getId().toString(), saved.getCode());
        return organizationMapper.toResponse(saved);
    }

    /**
     * 返回组织节点扁平列表，用于后台表格、选择器和 SCIM 列表映射。
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> list() {
        return organizations.findAll().stream().map(organizationMapper::toResponse).toList();
    }

    /**
     * 按组织 ID 查询组织详情。
     */
    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID organizationId) {
        return organizationMapper.toResponse(getEntity(organizationId));
    }

    /**
     * 将所有组织节点按父子关系组装成树形结构。
     */
    @Transactional(readOnly = true)
    public List<OrganizationTreeResponse> tree() {
        List<Organization> all = organizations.findAll().stream()
            .sorted(Comparator.comparing(Organization::getCode))
            .toList();
        Map<UUID, List<Organization>> childrenByParent = new LinkedHashMap<>();
        all.forEach(organization -> {
            UUID parentId = organization.getParent() == null ? null : organization.getParent().getId();
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(organization);
        });
        return childrenByParent.getOrDefault(null, List.of()).stream()
            .map(root -> toTreeResponse(root, childrenByParent))
            .toList();
    }

    /**
     * 查询组织成员，可选择递归包含子组织的用户。
     */
    @Transactional(readOnly = true)
    public List<OrganizationUserResponse> users(UUID organizationId, boolean includeDescendants) {
        Organization organization = getEntity(organizationId);
        Set<UUID> organizationIds = includeDescendants
            ? collectOrganizationSubtreeIds(organizationId)
            : Set.of(organization.getId());
        return organizationIds.stream()
            .flatMap(id -> users.findByOrganizationId(id).stream())
            .sorted(Comparator.comparing(UserAccount::getUsername))
            .map(organizationMapper::toUserResponse)
            .toList();
    }

    /**
     * 获取组织实体，供跨服务业务逻辑复用。
     */
    @Transactional(readOnly = true)
    public Organization getEntity(UUID id) {
        return organizations.findById(id)
            .orElseThrow(() -> new NotFoundException("Organization not found: " + id));
    }

    /**
     * 将组织实体转换为外部响应 DTO。
     */
    public OrganizationResponse toResponse(Organization organization) {
        return organizationMapper.toResponse(organization);
    }

    private Set<UUID> collectOrganizationSubtreeIds(UUID organizationId) {
        List<Organization> all = organizations.findAll();
        Map<UUID, List<Organization>> childrenByParent = new LinkedHashMap<>();
        all.forEach(organization -> {
            UUID parentId = organization.getParent() == null ? null : organization.getParent().getId();
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(organization);
        });
        Set<UUID> result = new LinkedHashSet<>();
        collectOrganizationSubtreeIds(organizationId, childrenByParent, result);
        return result;
    }

    private void collectOrganizationSubtreeIds(
        UUID organizationId,
        Map<UUID, List<Organization>> childrenByParent,
        Set<UUID> result
    ) {
        result.add(organizationId);
        childrenByParent.getOrDefault(organizationId, List.of())
            .forEach(child -> collectOrganizationSubtreeIds(child.getId(), childrenByParent, result));
    }

    private void validateParent(Organization organization, Organization parent) {
        Organization cursor = parent;
        while (cursor != null) {
            if (cursor.getId().equals(organization.getId())) {
                throw new IllegalArgumentException("Organization parent cannot be itself or its descendant");
            }
            cursor = cursor.getParent();
        }
    }

    private OrganizationTreeResponse toTreeResponse(Organization organization, Map<UUID, List<Organization>> childrenByParent) {
        List<OrganizationTreeResponse> children = childrenByParent.getOrDefault(organization.getId(), List.of()).stream()
            .map(child -> toTreeResponse(child, childrenByParent))
            .toList();
        return organizationMapper.toTreeResponse(organization, children);
    }
}
