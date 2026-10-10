package com.antiam.web;

import static com.antiam.dto.ScimDtos.ScimAuthenticationScheme;
import static com.antiam.dto.ScimDtos.ScimFeature;
import static com.antiam.dto.ScimDtos.ScimListResponse;
import static com.antiam.dto.ScimDtos.ScimPatchOperation;
import static com.antiam.dto.ScimDtos.ScimPatchRequest;
import static com.antiam.dto.ScimDtos.ScimResourceTypeResponse;
import static com.antiam.dto.ScimDtos.ScimSchemaAttribute;
import static com.antiam.dto.ScimDtos.ScimSchemaResponse;
import static com.antiam.dto.ScimDtos.ScimServiceProviderConfigResponse;
import static com.antiam.dto.ScimSourceDtos.GROUP_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.ORGANIZATION_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.USER_EXTENSION_SCHEMA;
import static com.antiam.dto.ScimSourceDtos.USER_SCHEMA;

import com.antiam.common.ScimException;
import com.antiam.config.IssuerResolver;
import com.antiam.dto.ScimSourceDtos.ScimReference;
import com.antiam.dto.ScimSourceDtos.SourceGroupRequest;
import com.antiam.dto.ScimSourceDtos.SourceGroupResponse;
import com.antiam.dto.ScimSourceDtos.SourceOrganizationRequest;
import com.antiam.dto.ScimSourceDtos.SourceOrganizationResponse;
import com.antiam.dto.ScimSourceDtos.SourceUserRequest;
import com.antiam.dto.ScimSourceDtos.SourceUserResponse;
import com.antiam.service.ScimIdentitySourceService;
import com.antiam.service.ScimIdentitySourceService.UserState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用 SCIM 身份源端点：第三方系统用身份源的同步令牌推送组织、用户与用户组。
 * 所有资源都限定在路径中的身份源内，错误按 SCIM Error 格式返回（见 {@link ScimSourceExceptionHandler}）。
 */
@RestController
@RequestMapping(
    value = ScimSourceController.BASE_PATH,
    produces = {"application/scim+json", "application/json"})
@RequiredArgsConstructor
@Tag(name = "SCIM 身份源同步", description = "第三方系统作为身份源，用同步令牌经 SCIM 2.0 推送组织、用户和用户组")
public class ScimSourceController {

    static final String BASE_PATH = "/scim/v2/sources/{sourceCode}";
    private static final int MAX_PAGE_SIZE = 500;
    private static final String AUTHORIZATION = "Authorization";

    private final ScimIdentitySourceService scim;
    private final IssuerResolver issuerResolver;

    // ---------------------------------------------------------------- Users

    @Operation(summary = "查询本源用户", description = "filter 支持 userName、externalId、displayName、active、emails.value、phoneNumbers.value；count 最大 500。")
    @GetMapping("/Users")
    ScimListResponse<SourceUserResponse> listUsers(
        @Parameter(description = "身份源编码") @PathVariable String sourceCode,
        @Parameter(description = "SCIM 过滤表达式，例如 externalId eq \"E10086\"") @RequestParam(required = false) String filter,
        @RequestParam(required = false) Integer startIndex,
        @RequestParam(required = false) Integer count,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        return ScimQuerySupport.listResponse(scim.listUsers(sourceId, location(request, sourceCode)), filter, startIndex, pageSize(count),
            ScimSourceController::supportsUserFilter, ScimSourceController::matchesUser);
    }

    @Operation(summary = "获取本源用户")
    @GetMapping("/Users/{id}")
    SourceUserResponse getUser(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        return scim.getUser(scim.authenticate(sourceCode, authorization), id, location(request, sourceCode));
    }

    @Operation(summary = "创建用户", description = "userName 全局唯一；externalId 本源内唯一；组织通过扩展属性 organization 指定。")
    @PostMapping("/Users")
    ResponseEntity<SourceUserResponse> createUser(
        @PathVariable String sourceCode,
        @Valid @RequestBody SourceUserRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        SourceUserResponse created = scim.createUser(scim.authenticate(sourceCode, authorization), body, location(request, sourceCode));
        return created(created, created.meta().location());
    }

    @Operation(summary = "替换用户", description = "整体替换显示名、邮箱、手机号、组织和启用状态；userName 不可修改，active 省略视为 true。")
    @PutMapping("/Users/{id}")
    SourceUserResponse replaceUser(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @Valid @RequestBody SourceUserRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        return scim.replaceUser(scim.authenticate(sourceCode, authorization), id, body, location(request, sourceCode));
    }

    @Operation(summary = "局部更新用户", description = "支持 active、displayName、name.formatted、emails、phoneNumbers、externalId 及组织扩展属性的 add/replace/remove。")
    @PatchMapping("/Users/{id}")
    SourceUserResponse patchUser(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @Valid @RequestBody ScimPatchRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        return scim.patchUser(sourceId, id, state -> body.operations().forEach(operation -> applyUserPatch(state, operation)), location(request, sourceCode));
    }

    @Operation(summary = "删除（停用）用户", description = "停用账号并回收会话、令牌和直接授权，账号保留，可通过 PUT/PATCH active=true 恢复。")
    @DeleteMapping("/Users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUser(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization
    ) {
        scim.deleteUser(scim.authenticate(sourceCode, authorization), id);
    }

    // ---------------------------------------------------------------- Organizations

    @Operation(summary = "查询本源组织", description = "filter 支持 externalId、displayName、parent.value。")
    @GetMapping("/Organizations")
    ScimListResponse<SourceOrganizationResponse> listOrganizations(
        @PathVariable String sourceCode,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false) Integer startIndex,
        @RequestParam(required = false) Integer count,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        return ScimQuerySupport.listResponse(scim.listOrganizations(sourceId, location(request, sourceCode)), filter, startIndex, pageSize(count),
            ScimSourceController::supportsOrganizationFilter, ScimSourceController::matchesOrganization);
    }

    @Operation(summary = "获取本源组织")
    @GetMapping("/Organizations/{id}")
    SourceOrganizationResponse getOrganization(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        return scim.getOrganization(scim.authenticate(sourceCode, authorization), id, location(request, sourceCode));
    }

    @Operation(summary = "创建组织", description = "parent.value 默认为上级组织的 externalId；挂到 IAM 已有组织下时设 parent.type 为 id。")
    @PostMapping("/Organizations")
    ResponseEntity<SourceOrganizationResponse> createOrganization(
        @PathVariable String sourceCode,
        @Valid @RequestBody SourceOrganizationRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        SourceOrganizationResponse created = scim.createOrganization(scim.authenticate(sourceCode, authorization), body, location(request, sourceCode));
        return created(created, created.meta().location());
    }

    @Operation(summary = "替换组织", description = "更新名称和上级组织；externalId 不可修改。")
    @PutMapping("/Organizations/{id}")
    SourceOrganizationResponse replaceOrganization(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @Valid @RequestBody SourceOrganizationRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        return scim.replaceOrganization(scim.authenticate(sourceCode, authorization), id, body, location(request, sourceCode));
    }

    @Operation(summary = "删除组织", description = "仅删除空组织；存在子组织或用户时返回 409。")
    @DeleteMapping("/Organizations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOrganization(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization
    ) {
        scim.deleteOrganization(scim.authenticate(sourceCode, authorization), id);
    }

    // ---------------------------------------------------------------- Groups

    @Operation(summary = "查询本源用户组", description = "filter 支持 externalId、displayName、members.value。")
    @GetMapping("/Groups")
    ScimListResponse<SourceGroupResponse> listGroups(
        @PathVariable String sourceCode,
        @RequestParam(required = false) String filter,
        @RequestParam(required = false) Integer startIndex,
        @RequestParam(required = false) Integer count,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        return ScimQuerySupport.listResponse(scim.listGroups(sourceId, location(request, sourceCode)), filter, startIndex, pageSize(count),
            ScimSourceController::supportsGroupFilter, ScimSourceController::matchesGroup);
    }

    @Operation(summary = "获取本源用户组")
    @GetMapping("/Groups/{id}")
    SourceGroupResponse getGroup(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        return scim.getGroup(scim.authenticate(sourceCode, authorization), id, location(request, sourceCode));
    }

    @Operation(summary = "创建用户组", description = "members[].value 为本源用户的 IAM id。")
    @PostMapping("/Groups")
    ResponseEntity<SourceGroupResponse> createGroup(
        @PathVariable String sourceCode,
        @Valid @RequestBody SourceGroupRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        SourceGroupResponse created = scim.createGroup(scim.authenticate(sourceCode, authorization), body, location(request, sourceCode));
        return created(created, created.meta().location());
    }

    @Operation(summary = "替换用户组", description = "整体替换名称与成员；externalId 不可修改。")
    @PutMapping("/Groups/{id}")
    SourceGroupResponse replaceGroup(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @Valid @RequestBody SourceGroupRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        List<String> members = body.members() == null ? List.of() : body.members().stream().map(member -> member.value()).toList();
        return scim.updateGroup(sourceId, id, body.externalId(), body.displayName(), members, location(request, sourceCode));
    }

    @Operation(summary = "局部更新用户组", description = "支持 displayName 替换，members 的 add/remove/replace，以及 members[value eq \"<id>\"] 移除。")
    @PatchMapping("/Groups/{id}")
    SourceGroupResponse patchGroup(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @Valid @RequestBody ScimPatchRequest body,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        UUID sourceId = scim.authenticate(sourceCode, authorization);
        Set<String> members = new LinkedHashSet<>(scim.groupMemberIds(sourceId, id));
        GroupPatch patch = new GroupPatch(members);
        for (ScimPatchOperation operation : body.operations()) {
            String op = ScimPatchSupport.op(operation);
            String path = ScimPatchSupport.path(operation.path(), GROUP_SCHEMA);
            if (path == null) {
                if (op.equals("remove")) {
                    throw ScimException.invalidValue("SCIM remove operation requires a path");
                }
                for (Map.Entry<?, ?> entry : ScimPatchSupport.attributes(operation).entrySet()) {
                    patch.apply(op, ScimPatchSupport.path(String.valueOf(entry.getKey()), GROUP_SCHEMA), entry.getValue());
                }
            } else {
                patch.apply(op, path, operation.value());
            }
        }
        return scim.updateGroup(sourceId, id, null, patch.displayName, patch.membersChanged ? new ArrayList<>(members) : null, location(request, sourceCode));
    }

    @Operation(summary = "删除用户组", description = "解除成员关系后删除用户组。")
    @DeleteMapping("/Groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(
        @PathVariable String sourceCode,
        @PathVariable UUID id,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization
    ) {
        scim.deleteGroup(scim.authenticate(sourceCode, authorization), id);
    }

    // ---------------------------------------------------------------- Discovery

    @Operation(summary = "SCIM 服务配置")
    @GetMapping("/ServiceProviderConfig")
    ScimServiceProviderConfigResponse serviceProviderConfig(
        @PathVariable String sourceCode,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
        HttpServletRequest request
    ) {
        scim.authenticate(sourceCode, authorization);
        String documentation = issuerResolver.resolve(request) + "/docs/directory-sync.html";
        return new ScimServiceProviderConfigResponse(
            List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
            documentation,
            new ScimFeature(true),
            new ScimFeature(false),
            new ScimFeature(true),
            new ScimFeature(false),
            new ScimFeature(false),
            new ScimFeature(false),
            List.of(new ScimAuthenticationScheme(
                "oauthbearertoken",
                "Sync token",
                "身份源同步令牌，在控制台身份源详情中生成",
                "https://www.rfc-editor.org/rfc/rfc6750",
                documentation,
                true)));
    }

    @Operation(summary = "SCIM 资源类型")
    @GetMapping("/ResourceTypes")
    List<ScimResourceTypeResponse> resourceTypes(
        @PathVariable String sourceCode,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization
    ) {
        scim.authenticate(sourceCode, authorization);
        List<String> schema = List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType");
        return List.of(
            new ScimResourceTypeResponse(schema, "User", "User", "/Users", "人员", USER_SCHEMA),
            new ScimResourceTypeResponse(schema, "Group", "Group", "/Groups", "用户组", GROUP_SCHEMA),
            new ScimResourceTypeResponse(schema, "Organization", "Organization", "/Organizations", "组织", ORGANIZATION_SCHEMA));
    }

    @Operation(summary = "SCIM Schema")
    @GetMapping("/Schemas")
    List<ScimSchemaResponse> schemas(
        @PathVariable String sourceCode,
        @RequestHeader(value = AUTHORIZATION, required = false) String authorization
    ) {
        scim.authenticate(sourceCode, authorization);
        List<String> schema = List.of("urn:ietf:params:scim:schemas:core:2.0:Schema");
        return List.of(
            new ScimSchemaResponse(schema, USER_SCHEMA, "User", "人员", List.of(
                new ScimSchemaAttribute("userName", "string", false, true, "immutable"),
                new ScimSchemaAttribute("externalId", "string", false, false, "readWrite"),
                new ScimSchemaAttribute("displayName", "string", false, false, "readWrite"),
                new ScimSchemaAttribute("name", "complex", false, false, "readWrite"),
                new ScimSchemaAttribute("emails", "complex", true, false, "readWrite"),
                new ScimSchemaAttribute("phoneNumbers", "complex", true, false, "readWrite"),
                new ScimSchemaAttribute("active", "boolean", false, false, "readWrite"))),
            new ScimSchemaResponse(schema, USER_EXTENSION_SCHEMA, "User extension", "人员扩展属性", List.of(
                new ScimSchemaAttribute("organization", "complex", false, false, "readWrite"))),
            new ScimSchemaResponse(schema, GROUP_SCHEMA, "Group", "用户组", List.of(
                new ScimSchemaAttribute("externalId", "string", false, true, "immutable"),
                new ScimSchemaAttribute("displayName", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("members", "complex", true, false, "readWrite"))),
            new ScimSchemaResponse(schema, ORGANIZATION_SCHEMA, "Organization", "组织", List.of(
                new ScimSchemaAttribute("externalId", "string", false, true, "immutable"),
                new ScimSchemaAttribute("displayName", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("parent", "complex", false, false, "readWrite"))));
    }

    // ---------------------------------------------------------------- 内部辅助

    private String location(HttpServletRequest request, String sourceCode) {
        return issuerResolver.resolve(request) + "/scim/v2/sources/" + sourceCode;
    }

    private static <T> ResponseEntity<T> created(T body, String location) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Location", location).body(body);
    }

    private static int pageSize(Integer count) {
        if (count == null || count < 0) {
            return MAX_PAGE_SIZE;
        }
        return Math.min(count, MAX_PAGE_SIZE);
    }

    private static void applyUserPatch(UserState state, ScimPatchOperation operation) {
        String op = ScimPatchSupport.op(operation);
        String path = ScimPatchSupport.path(operation.path(), USER_SCHEMA);
        if (path == null) {
            if (op.equals("remove")) {
                throw ScimException.invalidValue("SCIM remove operation requires a path");
            }
            for (Map.Entry<?, ?> entry : ScimPatchSupport.attributes(operation).entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.equalsIgnoreCase(USER_EXTENSION_SCHEMA)) {
                    if (entry.getValue() instanceof Map<?, ?> extension) {
                        applyUserAttribute(state, op, "organization", extension.get("organization"));
                    }
                    continue;
                }
                applyUserAttribute(state, op, ScimPatchSupport.path(key, USER_SCHEMA), entry.getValue());
            }
            return;
        }
        String extensionPrefix = USER_EXTENSION_SCHEMA.toLowerCase() + ":";
        if (path.startsWith(extensionPrefix)) {
            path = path.substring(extensionPrefix.length());
        }
        applyUserAttribute(state, op, path, operation.value());
    }

    private static void applyUserAttribute(UserState state, String op, String path, Object value) {
        boolean remove = op.equals("remove");
        switch (path) {
            case "active" -> state.active(remove ? Boolean.FALSE : ScimPatchSupport.bool(value));
            case "displayname", "name.formatted" -> state.displayName(remove ? null : ScimPatchSupport.text(value));
            case "name" -> state.displayName(remove || !(value instanceof Map<?, ?> name) ? null : ScimPatchSupport.text(name.get("formatted")));
            case "externalid" -> state.externalId(remove ? null : ScimPatchSupport.text(value));
            case "username" -> {
                if (remove) {
                    throw ScimException.mutability("userName cannot be removed");
                }
                state.userName(ScimPatchSupport.text(value));
            }
            case "organization", "organization.value" -> state.organization(remove ? null : reference(value));
            default -> {
                if (path.equals("emails") || path.startsWith("emails[") || path.startsWith("emails.")) {
                    state.email(remove ? null : ScimPatchSupport.text(value));
                } else if (path.equals("phonenumbers") || path.startsWith("phonenumbers[") || path.startsWith("phonenumbers.")) {
                    state.mobile(remove ? null : ScimPatchSupport.text(value));
                }
            }
        }
    }

    private static ScimReference reference(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return new ScimReference(ScimPatchSupport.text(map.get("value")), type == null ? null : String.valueOf(type));
        }
        String text = ScimPatchSupport.text(value);
        return text == null ? null : new ScimReference(text, null);
    }

    private static boolean supportsUserFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "userName", "externalId", "displayName", "active", "emails.value", "phoneNumbers.value" -> true;
            default -> false;
        };
    }

    private static boolean matchesUser(SourceUserResponse user, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "userName" -> filter.matches(user.userName());
            case "externalId" -> filter.matches(user.externalId());
            case "displayName" -> filter.matches(user.displayName());
            case "active" -> filter.matches(String.valueOf(user.active()));
            case "emails.value" -> user.emails().stream().anyMatch(email -> filter.matches(email.value()));
            case "phoneNumbers.value" -> user.phoneNumbers().stream().anyMatch(phone -> filter.matches(phone.value()));
            default -> false;
        };
    }

    private static boolean supportsOrganizationFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId", "displayName", "parent.value" -> true;
            default -> false;
        };
    }

    private static boolean matchesOrganization(SourceOrganizationResponse organization, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId" -> filter.matches(organization.externalId());
            case "displayName" -> filter.matches(organization.displayName());
            case "parent.value" -> filter.matches(organization.parent() == null ? null : organization.parent().value());
            default -> false;
        };
    }

    private static boolean supportsGroupFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId", "displayName", "members.value" -> true;
            default -> false;
        };
    }

    private static boolean matchesGroup(SourceGroupResponse group, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId" -> filter.matches(group.externalId());
            case "displayName" -> filter.matches(group.displayName());
            case "members.value" -> group.members().stream().anyMatch(member -> filter.matches(member.value()));
            default -> false;
        };
    }

    /** 用户组 PATCH 的累积状态，规则与全局 SCIM 用户组一致。 */
    private static final class GroupPatch {
        private final Set<String> members;
        private String displayName;
        private boolean membersChanged;

        private GroupPatch(Set<String> members) {
            this.members = members;
        }

        void apply(String op, String path, Object value) {
            if (path.equals("displayname")) {
                if (op.equals("remove")) {
                    throw ScimException.invalidValue("Group displayName is required");
                }
                displayName = ScimPatchSupport.text(value);
                return;
            }
            if (path.equals("externalid")) {
                throw ScimException.mutability("Group externalId cannot be changed");
            }
            String filtered = ScimPatchSupport.filteredValue(path, "members");
            if (filtered != null) {
                if (!op.equals("remove")) {
                    throw ScimException.invalidValue("Only remove is supported for filtered members path");
                }
                members.removeIf(member -> member.equalsIgnoreCase(filtered));
                membersChanged = true;
                return;
            }
            if (!path.equals("members")) {
                return;
            }
            List<String> values = ScimPatchSupport.values(value);
            switch (op) {
                case "add" -> members.addAll(values);
                case "replace" -> {
                    members.clear();
                    members.addAll(values);
                }
                default -> {
                    if (values.isEmpty()) {
                        members.clear();
                    } else {
                        values.forEach(id -> members.removeIf(member -> member.equalsIgnoreCase(id)));
                    }
                }
            }
            membersChanged = true;
        }
    }
}
