package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimGroupRequest;
import static com.antiam.dto.ScimDtos.ScimGroupResponse;
import static com.antiam.dto.ScimDtos.ScimListResponse;
import static com.antiam.dto.ScimDtos.ScimMember;
import static com.antiam.dto.ScimDtos.ScimPatchOperation;
import static com.antiam.dto.ScimDtos.ScimPatchRequest;

import com.antiam.service.ScimGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scim/v2/Groups")
@RequiredArgsConstructor
@Tag(name = "SCIM 用户组", description = "SCIM 2.0 用户组资源同步接口")
public class ScimGroupController {

    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";

    private final ScimGroupService groups;

    /**
     * 查询 SCIM 用户组资源列表，支持 displayName 和 members.value 过滤。
     */
    @Operation(summary = "查询 SCIM 用户组列表", description = "支持 filter、startIndex、count 参数，filter 支持 displayName、members.value 的 eq/co/sw 操作。")
    @GetMapping
    ScimListResponse<ScimGroupResponse> list(
        @Parameter(description = "SCIM 过滤表达式，例如 displayName co \"admin\" 或 members.value eq \"<userId>\"")
        @RequestParam(required = false) String filter,
        @Parameter(description = "分页起始序号，SCIM 规范使用 1-based，默认 1")
        @RequestParam(required = false) Integer startIndex,
        @Parameter(description = "返回数量，默认返回过滤后的全部资源")
        @RequestParam(required = false) Integer count
    ) {
        List<ScimGroupResponse> resources = groups.list();
        return ScimQuerySupport.listResponse(resources, filter, startIndex, count, this::supportsFilter, this::matchesFilter);
    }

    /**
     * 按内部 UUID 查询单个 SCIM 用户组资源。
     */
    @Operation(summary = "获取 SCIM 用户组详情", description = "根据用户组 UUID 返回 SCIM Group 资源。")
    @GetMapping("/{groupId}")
    ScimGroupResponse get(@Parameter(description = "用户组 UUID") @PathVariable UUID groupId) {
        return groups.get(groupId);
    }

    /**
     * 创建 SCIM 用户组，并按成员 ID 建立用户组成员关系。
     */
    @Operation(summary = "创建 SCIM 用户组", description = "根据 SCIM Group 请求创建内部用户组，并可绑定成员。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScimGroupResponse create(
        @Parameter(description = "SCIM Group 创建请求") @Valid @RequestBody CreateScimGroupRequest request,
        Principal principal
    ) {
        return groups.create(request, principal.getName());
    }

    /**
     * 使用完整 SCIM Group 资源替换用户组名称和成员。
     */
    @Operation(summary = "替换 SCIM 用户组", description = "以请求体整体替换用户组名称和成员列表。")
    @PutMapping("/{groupId}")
    ScimGroupResponse replace(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "SCIM Group 资源") @Valid @RequestBody CreateScimGroupRequest request,
        Principal principal
    ) {
        List<String> members = request.members() == null
            ? List.of()
            : request.members().stream().map(ScimMember::value).toList();
        return groups.update(groupId, request.displayName(), members, principal.getName());
    }

    /**
     * 按 SCIM PATCH 操作局部更新用户组名称和成员。
     */
    @Operation(summary = "局部更新 SCIM 用户组", description = "支持 displayName 替换，以及 members 的 add/remove/replace 和 members[value eq \"<userId>\"] 移除。")
    @PatchMapping("/{groupId}")
    ScimGroupResponse patch(
        @Parameter(description = "用户组 UUID") @PathVariable UUID groupId,
        @Parameter(description = "SCIM PatchOp 请求") @Valid @RequestBody ScimPatchRequest request,
        Principal principal
    ) {
        Set<String> members = new LinkedHashSet<>(groups.memberIds(groupId));
        String[] displayName = {null};
        boolean[] membersChanged = {false};
        for (ScimPatchOperation operation : request.operations()) {
            String op = ScimPatchSupport.op(operation);
            String path = ScimPatchSupport.path(operation.path(), GROUP_SCHEMA_URN);
            if (path == null) {
                if (op.equals("remove")) {
                    throw new IllegalArgumentException("SCIM remove operation requires a path");
                }
                for (Map.Entry<?, ?> entry : ScimPatchSupport.attributes(operation).entrySet()) {
                    String attribute = ScimPatchSupport.path(String.valueOf(entry.getKey()), GROUP_SCHEMA_URN);
                    applyAttribute(op, attribute, entry.getValue(), members, displayName, membersChanged);
                }
            } else {
                applyAttribute(op, path, operation.value(), members, displayName, membersChanged);
            }
        }
        return groups.update(groupId, displayName[0], membersChanged[0] ? new ArrayList<>(members) : null, principal.getName());
    }

    /**
     * 删除 SCIM 用户组。
     */
    @Operation(summary = "删除 SCIM 用户组", description = "删除内部用户组并解除成员关系。")
    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(description = "用户组 UUID") @PathVariable UUID groupId, Principal principal) {
        groups.delete(groupId, principal.getName());
    }

    private void applyAttribute(String op, String path, Object value, Set<String> members, String[] displayName, boolean[] membersChanged) {
        if (path.equals("displayname")) {
            if (op.equals("remove")) {
                throw new IllegalArgumentException("SCIM group displayName is required");
            }
            displayName[0] = ScimPatchSupport.text(value);
            return;
        }
        String filtered = ScimPatchSupport.filteredValue(path, "members");
        if (filtered != null) {
            if (!op.equals("remove")) {
                throw new IllegalArgumentException("Only remove is supported for filtered members path");
            }
            members.removeIf(member -> member.equalsIgnoreCase(filtered));
            membersChanged[0] = true;
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
        membersChanged[0] = true;
    }

    private boolean supportsFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "displayName", "members.value" -> true;
            default -> false;
        };
    }

    private boolean matchesFilter(ScimGroupResponse group, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "displayName" -> filter.matches(group.displayName());
            case "members.value" -> group.members().stream().anyMatch(member -> filter.matches(member.value()));
            default -> false;
        };
    }
}
