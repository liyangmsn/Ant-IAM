package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimOrganizationRequest;
import static com.antiam.dto.ScimDtos.ScimListResponse;
import static com.antiam.dto.ScimDtos.ScimOrganizationResponse;

import com.antiam.service.ScimOrganizationService;
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
@RequestMapping("/scim/v2/Organizations")
@RequiredArgsConstructor
@Tag(name = "SCIM 组织", description = "SCIM 2.0 组织资源同步接口")
public class ScimOrganizationController {

    private final ScimOrganizationService organizations;

    /**
     * 查询 SCIM 组织资源列表，支持 externalId、displayName、parentId 过滤。
     */
    @Operation(summary = "查询 SCIM 组织列表", description = "支持 filter、startIndex、count 参数，filter 支持 externalId、displayName、parentId 的 eq/co/sw 操作。")
    @GetMapping
    ScimListResponse<ScimOrganizationResponse> list(
        @Parameter(description = "SCIM 过滤表达式，例如 externalId eq \"engineering\" 或 displayName co \"研发\"")
        @RequestParam(required = false) String filter,
        @Parameter(description = "分页起始序号，SCIM 规范使用 1-based，默认 1")
        @RequestParam(required = false) Integer startIndex,
        @Parameter(description = "返回数量，默认返回过滤后的全部资源")
        @RequestParam(required = false) Integer count
    ) {
        List<ScimOrganizationResponse> resources = organizations.list();
        return ScimQuerySupport.listResponse(resources, filter, startIndex, count, this::supportsFilter, this::matchesFilter);
    }

    /**
     * 按内部 UUID 查询单个 SCIM 组织资源。
     */
    @Operation(summary = "获取 SCIM 组织详情", description = "根据组织 UUID 返回 SCIM Organization 资源。")
    @GetMapping("/{organizationId}")
    ScimOrganizationResponse get(@Parameter(description = "组织 UUID") @PathVariable UUID organizationId) {
        return organizations.get(organizationId);
    }

    /**
     * 创建 SCIM 组织，并映射到内部组织目录。
     */
    @Operation(summary = "创建 SCIM 组织", description = "根据 SCIM Organization 请求创建内部组织节点。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScimOrganizationResponse create(
        @Parameter(description = "SCIM Organization 创建请求") @Valid @RequestBody CreateScimOrganizationRequest request,
        Principal principal
    ) {
        return organizations.create(request, principal.getName());
    }

    private boolean supportsFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId", "displayName", "parentId" -> true;
            default -> false;
        };
    }

    private boolean matchesFilter(ScimOrganizationResponse organization, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "externalId" -> filter.matches(organization.externalId());
            case "displayName" -> filter.matches(organization.displayName());
            case "parentId" -> filter.matches(organization.parentId() == null ? null : organization.parentId().toString());
            default -> false;
        };
    }
}
