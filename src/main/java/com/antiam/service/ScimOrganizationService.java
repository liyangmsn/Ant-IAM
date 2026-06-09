package com.antiam.service;

import static com.antiam.dto.ScimDtos.CreateScimOrganizationRequest;
import static com.antiam.dto.ScimDtos.ScimOrganizationResponse;

import com.antiam.dto.OrganizationDtos.OrganizationResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScimOrganizationService {

    private static final List<String> ORGANIZATION_SCHEMA = List.of("urn:antiam:params:scim:schemas:extension:2.0:Organization");

    private final OrganizationService organizations;

    /**
     * 返回所有内部组织节点的 SCIM Organization 视图，用于目录同步。
     */
    @Transactional(readOnly = true)
    public List<ScimOrganizationResponse> list() {
        return organizations.list().stream().map(this::toResponse).toList();
    }

    /**
     * 按组织 ID 返回单个 SCIM Organization 资源，供外部系统核对组织状态。
     */
    @Transactional(readOnly = true)
    public ScimOrganizationResponse get(UUID organizationId) {
        return toResponse(organizations.get(organizationId));
    }

    /**
     * 创建 SCIM Organization，并映射为内部组织目录节点。
     */
    @Transactional
    public ScimOrganizationResponse create(CreateScimOrganizationRequest request, String actor) {
        OrganizationResponse saved = organizations.createScimOrganization(
            request.externalId(),
            request.displayName(),
            request.parentId(),
            actor);
        return toResponse(saved);
    }

    private ScimOrganizationResponse toResponse(OrganizationResponse organization) {
        return new ScimOrganizationResponse(
            ORGANIZATION_SCHEMA,
            organization.id().toString(),
            organization.code(),
            organization.name(),
            organization.parentId());
    }
}
