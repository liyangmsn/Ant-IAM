package com.antiam.mapper;

import com.antiam.domain.Organization;
import com.antiam.domain.UserAccount;
import com.antiam.dto.OrganizationDtos.OrganizationResponse;
import com.antiam.dto.OrganizationDtos.OrganizationTreeResponse;
import com.antiam.dto.OrganizationDtos.OrganizationUserResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrganizationMapper {

    /**
     * 将组织实体转换为扁平组织响应。
     */
    @Mapping(target = "parentId", source = "parent.id")
    OrganizationResponse toResponse(Organization organization);

    /**
     * 将用户实体转换为组织成员投影。
     */
    @Mapping(target = "userId", source = "id")
    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "tenantId", source = "tenant.id")
    OrganizationUserResponse toUserResponse(UserAccount user);

    /**
     * 将组织实体和已组装的子节点转换为树节点响应。
     */
    @Mapping(target = "parentId", source = "organization.parent.id")
    @Mapping(target = "children", source = "children")
    OrganizationTreeResponse toTreeResponse(Organization organization, List<OrganizationTreeResponse> children);
}
