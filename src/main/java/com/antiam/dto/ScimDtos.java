package com.antiam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public final class ScimDtos {
    private ScimDtos() {
    }

    public record ScimName(
        @Schema(description = "格式化姓名", example = "张三")
        String formatted,
        @Schema(description = "姓氏", example = "张")
        String familyName,
        @Schema(description = "名字", example = "三")
        String givenName
    ) {
    }

    public record ScimEmail(
        @Schema(description = "邮箱地址", example = "zhangsan@example.com")
        @NotBlank String value,
        @Schema(description = "邮箱类型，例如 work 或 home", example = "work")
        String type,
        @Schema(description = "是否主邮箱")
        Boolean primary
    ) {
    }

    public record ScimPhoneNumber(
        @Schema(description = "电话号码", example = "13800000000")
        String value,
        @Schema(description = "电话类型，例如 mobile 或 work", example = "mobile")
        String type,
        @Schema(description = "是否主号码")
        Boolean primary
    ) {
    }

    public record CreateScimUserRequest(
        @Schema(description = "SCIM 用户名，映射内部登录账号", example = "zhangsan")
        @NotBlank String userName,
        @Schema(description = "显示名称", example = "张三")
        String displayName,
        @Schema(description = "姓名结构")
        @Valid ScimName name,
        @Schema(description = "邮箱列表")
        List<@Valid ScimEmail> emails,
        @Schema(description = "电话号码列表")
        List<@Valid ScimPhoneNumber> phoneNumbers,
        @Schema(description = "是否启用；为空时默认启用")
        Boolean active
    ) {
    }

    public record ScimUserResponse(
        List<String> schemas,
        String id,
        String userName,
        String displayName,
        ScimName name,
        List<ScimEmail> emails,
        List<ScimPhoneNumber> phoneNumbers,
        Boolean active
    ) {
    }

    public record ScimMember(
        @Schema(description = "成员资源 ID，通常为用户 UUID")
        @NotBlank String value,
        @Schema(description = "成员显示名称", example = "张三")
        String display,
        @Schema(description = "成员资源引用地址")
        @JsonProperty("$ref") String ref,
        @Schema(description = "成员类型，例如 User", example = "User")
        String type
    ) {
    }

    public record CreateScimGroupRequest(
        @Schema(description = "SCIM 用户组显示名称，映射内部用户组名称和编码", example = "管理员")
        @NotBlank String displayName,
        @Schema(description = "用户组成员列表")
        List<@Valid ScimMember> members
    ) {
    }

    public record ScimGroupResponse(
        List<String> schemas,
        String id,
        String displayName,
        List<ScimMember> members
    ) {
    }

    public record CreateScimOrganizationRequest(
        @Schema(description = "SCIM 外部组织 ID，映射内部组织编码", example = "rd")
        @NotBlank String externalId,
        @Schema(description = "组织显示名称", example = "研发部")
        @NotBlank String displayName,
        @Schema(description = "父组织 UUID；为空表示根组织")
        UUID parentId
    ) {
    }

    public record ScimOrganizationResponse(
        List<String> schemas,
        String id,
        String externalId,
        String displayName,
        UUID parentId
    ) {
    }

    public record ScimListResponse<T>(
        List<String> schemas,
        int totalResults,
        int startIndex,
        int itemsPerPage,
        List<T> Resources
    ) {
    }

    public record ScimFeature(Boolean supported) {
    }

    public record ScimAuthenticationScheme(
        String type,
        String name,
        String description,
        String specUri,
        String documentationUri,
        Boolean primary
    ) {
    }

    public record ScimServiceProviderConfigResponse(
        List<String> schemas,
        String documentationUri,
        ScimFeature patch,
        ScimFeature bulk,
        ScimFeature filter,
        ScimFeature changePassword,
        ScimFeature sort,
        ScimFeature etag,
        List<ScimAuthenticationScheme> authenticationSchemes
    ) {
    }

    public record ScimResourceTypeResponse(
        List<String> schemas,
        String id,
        String name,
        String endpoint,
        String description,
        String schema
    ) {
    }

    public record ScimSchemaAttribute(String name, String type, Boolean multiValued, Boolean required, String mutability) {
    }

    public record ScimSchemaResponse(
        List<String> schemas,
        String id,
        String name,
        String description,
        List<ScimSchemaAttribute> attributes
    ) {
    }
}
