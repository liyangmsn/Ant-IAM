package com.antiam.web;

import static com.antiam.dto.OrganizationDtos.CreateOrganizationRequest;
import static com.antiam.dto.OrganizationDtos.OrganizationResponse;
import static com.antiam.dto.OrganizationDtos.OrganizationTreeResponse;
import static com.antiam.dto.OrganizationDtos.OrganizationUserResponse;
import static com.antiam.dto.OrganizationDtos.UpdateOrganizationRequest;

import com.antiam.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "组织目录", description = "组织节点、组织树和组织成员查询接口")
public class OrganizationController {

    private final OrganizationService organizations;

    /**
     * 查询全部组织节点，返回扁平结构。
     */
    @Operation(summary = "查询组织列表", description = "返回所有组织节点的扁平列表，适合后台表格和下拉选择场景。")
    @GetMapping
    List<OrganizationResponse> list() {
        return organizations.list();
    }

    /**
     * 查询完整组织树，按父子关系组装层级结构。
     */
    @Operation(summary = "查询组织树", description = "返回从根组织开始的树形组织结构。")
    @GetMapping("/tree")
    List<OrganizationTreeResponse> tree() {
        return organizations.tree();
    }

    /**
     * 按组织 ID 查询单个组织节点详情。
     */
    @Operation(summary = "获取组织详情", description = "根据组织 UUID 返回组织节点详情。")
    @GetMapping("/{organizationId}")
    OrganizationResponse get(@Parameter(description = "组织 UUID") @PathVariable UUID organizationId) {
        return organizations.get(organizationId);
    }

    /**
     * 查询组织下用户，可选择包含所有子组织成员。
     */
    @Operation(summary = "查询组织成员", description = "返回指定组织下的用户列表，可通过 includeDescendants 包含子组织成员。")
    @GetMapping("/{organizationId}/users")
    List<OrganizationUserResponse> users(
        @Parameter(description = "组织 UUID") @PathVariable UUID organizationId,
        @Parameter(description = "是否包含所有子组织成员，默认 false")
        @RequestParam(defaultValue = "false") boolean includeDescendants
    ) {
        return organizations.users(organizationId, includeDescendants);
    }

    /**
     * 创建组织节点，并可指定父组织。
     */
    @Operation(summary = "创建组织", description = "创建组织节点；parentId 为空时创建根组织。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse create(
        @Parameter(description = "组织创建请求") @Valid @RequestBody CreateOrganizationRequest request,
        Principal principal
    ) {
        return organizations.create(request, principal.getName());
    }

    /**
     * 更新组织名称和父级关系，防止形成循环父子关系。
     */
    @Operation(summary = "更新组织", description = "更新组织名称和父组织；服务端会校验父级不能是自身或子孙节点。")
    @PutMapping("/{organizationId}")
    OrganizationResponse update(
        @Parameter(description = "组织 UUID") @PathVariable UUID organizationId,
        @Parameter(description = "组织更新请求") @Valid @RequestBody UpdateOrganizationRequest request,
        Principal principal
    ) {
        return organizations.update(organizationId, request, principal.getName());
    }

    /**
     * 删除空组织节点。
     */
    @Operation(summary = "删除组织", description = "删除没有子组织且没有成员的组织节点。")
    @DeleteMapping("/{organizationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
        @Parameter(description = "组织 UUID") @PathVariable UUID organizationId,
        Principal principal
    ) {
        organizations.delete(organizationId, principal.getName());
    }
}
