package com.antiam.dto;

import static com.antiam.dto.ScimDtos.ScimEmail;
import static com.antiam.dto.ScimDtos.ScimMember;
import static com.antiam.dto.ScimDtos.ScimName;
import static com.antiam.dto.ScimDtos.ScimPhoneNumber;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 身份源专属 SCIM 端点（/scim/v2/sources/{code}/...）使用的资源模型。
 * 与全局 SCIM 资源相比，增加了 externalId、组织归属和按 externalId 引用的上级组织。
 */
public final class ScimSourceDtos {

    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    public static final String ORGANIZATION_SCHEMA = "urn:antiam:params:scim:schemas:extension:2.0:Organization";
    public static final String USER_EXTENSION_SCHEMA = "urn:antiam:params:scim:schemas:extension:2.0:User";
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimSourceDtos() {
    }

    @Schema(description = "资源引用：value 默认为身份源内的 externalId；type 为 id 时 value 为 IAM 内部 UUID")
    public record ScimReference(
        @NotBlank @Size(max = 256) String value,
        @Schema(description = "externalId（默认）或 id") String type,
        @Schema(description = "展示名称，只读") String display
    ) {
        public ScimReference(String value, String type) {
            this(value, type, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScimUserExtension(
        @Schema(description = "所属组织") @Valid ScimReference organization
    ) {
    }

    /** created、lastModified 为 ISO-8601 UTC 时间（SCIM 要求），不使用控制台接口的本地时间格式。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScimMeta(String resourceType, String created, String lastModified, String location) {
    }

    public record SourceUserRequest(
        List<String> schemas,
        @Schema(description = "第三方系统内的人员 ID，本源内唯一", example = "E10086") @Size(max = 256) String externalId,
        @Schema(description = "登录用户名，全局唯一，创建后不可修改", example = "zhangsan") @NotBlank @Size(max = 128) String userName,
        @Schema(description = "显示名称", example = "张三") @Size(max = 256) String displayName,
        @Valid ScimName name,
        List<@Valid ScimEmail> emails,
        List<@Valid ScimPhoneNumber> phoneNumbers,
        @Schema(description = "是否启用；false 时停用账号并回收会话、令牌和直接授权") Boolean active,
        @JsonProperty(USER_EXTENSION_SCHEMA) @Valid ScimUserExtension enterprise
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceUserResponse(
        List<String> schemas,
        String id,
        String externalId,
        String userName,
        String displayName,
        ScimName name,
        List<ScimEmail> emails,
        List<ScimPhoneNumber> phoneNumbers,
        Boolean active,
        @JsonProperty(USER_EXTENSION_SCHEMA) ScimUserExtension enterprise,
        ScimMeta meta
    ) {
    }

    public record SourceGroupRequest(
        List<String> schemas,
        @Schema(description = "第三方系统内的用户组 ID，本源内唯一", example = "G-SALES") @NotBlank @Size(max = 256) String externalId,
        @Schema(description = "用户组名称", example = "销售团队") @NotBlank @Size(max = 256) String displayName,
        @Schema(description = "成员，value 为本源用户的 IAM id") List<@Valid ScimMember> members
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceGroupResponse(
        List<String> schemas,
        String id,
        String externalId,
        String displayName,
        List<ScimMember> members,
        ScimMeta meta
    ) {
    }

    public record SourceOrganizationRequest(
        List<String> schemas,
        @Schema(description = "第三方系统内的组织 ID，本源内唯一", example = "D100") @NotBlank @Size(max = 256) String externalId,
        @Schema(description = "组织名称", example = "华东销售部") @NotBlank @Size(max = 256) String displayName,
        @Schema(description = "上级组织；省略表示根组织") @Valid ScimReference parent
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceOrganizationResponse(
        List<String> schemas,
        String id,
        String externalId,
        String displayName,
        ScimReference parent,
        ScimMeta meta
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScimError(List<String> schemas, String status, String scimType, String detail) {
        public static ScimError of(int status, String scimType, String detail) {
            return new ScimError(List.of(ERROR_SCHEMA), String.valueOf(status), scimType, detail);
        }
    }

    public record SyncTokenResponse(
        @Schema(description = "同步令牌明文，只在生成时返回一次") String token,
        @Schema(description = "SCIM 基础地址") String baseUrl
    ) {
    }

    public record SyncTokenStatusResponse(
        @Schema(description = "是否已生成有效的同步令牌") boolean issued,
        @Schema(description = "SCIM 基础地址") String baseUrl,
        @Schema(description = "最近一次推送时间") Instant lastSyncedAt,
        @Schema(description = "本源当前的组织数") long organizations,
        @Schema(description = "本源当前的用户数") long users,
        @Schema(description = "本源当前的用户组数") long groups
    ) {
    }
}
