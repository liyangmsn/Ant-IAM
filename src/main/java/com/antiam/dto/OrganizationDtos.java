package com.antiam.dto;

import com.antiam.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {
    private OrganizationDtos() {
    }

    public record CreateOrganizationRequest(
        @Schema(description = "组织编码，同级或系统内唯一", example = "rd")
        @NotBlank String code,
        @Schema(description = "组织名称", example = "研发部")
        @NotBlank String name,
        @Schema(description = "父组织 UUID；为空表示根组织")
        UUID parentId
    ) {
    }

    public record UpdateOrganizationRequest(
        @Schema(description = "组织名称", example = "研发部")
        @NotBlank String name,
        @Schema(description = "父组织 UUID；为空表示根组织")
        UUID parentId
    ) {
    }

    public record OrganizationResponse(UUID id, String code, String name, UUID parentId) {
    }

    public record OrganizationUserResponse(
        UUID userId,
        String username,
        String displayName,
        String email,
        String mobile,
        AccountStatus status,
        UUID organizationId,
        UUID tenantId
    ) {
    }

    public record OrganizationTreeResponse(
        UUID id,
        String code,
        String name,
        UUID parentId,
        List<OrganizationTreeResponse> children
    ) {
    }
}
