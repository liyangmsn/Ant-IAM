package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimGroupRequest;
import static com.antiam.dto.ScimDtos.ScimGroupResponse;
import static com.antiam.dto.ScimDtos.ScimListResponse;

import com.antiam.service.ScimGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
