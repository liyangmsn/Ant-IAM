package com.antiam.web;

import static com.antiam.dto.TenantDtos.CreateTenantRequest;
import static com.antiam.dto.TenantDtos.TenantResponse;
import static com.antiam.dto.TenantDtos.TenantSettingResponse;
import static com.antiam.dto.TenantDtos.UpdateTenantRequest;
import static com.antiam.dto.TenantDtos.UpsertTenantSettingRequest;

import com.antiam.domain.SettingValueType;
import com.antiam.service.TenantService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "租户管理", description = "租户生命周期和租户级配置管理接口")
public class TenantController {

    private final TenantService tenants;

    /**
     * 查询租户列表。
     */
    @Operation(summary = "查询租户列表", description = "返回系统中的全部租户。")
    @GetMapping
    List<TenantResponse> list() {
        return tenants.list();
    }

    /**
     * 查询租户详情。
     */
    @Operation(summary = "获取租户详情", description = "根据租户 UUID 返回租户基础信息。")
    @GetMapping("/{tenantId}")
    TenantResponse get(@Parameter(description = "租户 UUID") @PathVariable UUID tenantId) {
        return tenants.get(tenantId);
    }

    /**
     * 创建租户。
     */
    @Operation(summary = "创建租户", description = "创建新的租户空间。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantResponse create(
        @Parameter(description = "租户创建请求") @Valid @RequestBody CreateTenantRequest request,
        Principal principal
    ) {
        return tenants.create(request, principal.getName());
    }

    /**
     * 更新租户资料。
     */
    @Operation(summary = "更新租户", description = "更新租户名称、域名等基础信息。")
    @PutMapping("/{tenantId}")
    TenantResponse update(
        @Parameter(description = "租户 UUID") @PathVariable UUID tenantId,
        @Parameter(description = "租户更新请求") @Valid @RequestBody UpdateTenantRequest request,
        Principal principal
    ) {
        return tenants.update(tenantId, request, principal.getName());
    }

    /**
     * 激活租户。
     */
    @Operation(summary = "激活租户", description = "将租户恢复为可用状态。")
    @PostMapping("/{tenantId}/activate")
    TenantResponse activate(@Parameter(description = "租户 UUID") @PathVariable UUID tenantId, Principal principal) {
        return tenants.activate(tenantId, principal.getName());
    }

    /**
     * 暂停租户。
     */
    @Operation(summary = "暂停租户", description = "将租户置为暂停状态。")
    @PostMapping("/{tenantId}/suspend")
    TenantResponse suspend(@Parameter(description = "租户 UUID") @PathVariable UUID tenantId, Principal principal) {
        return tenants.suspend(tenantId, principal.getName());
    }

    /**
     * 查询租户级配置。
     */
    @Operation(summary = "查询租户配置", description = "查询指定租户下的配置项，支持分类、类型、敏感标记和关键字过滤。")
    @GetMapping("/{tenantId}/settings")
    List<TenantSettingResponse> settings(
        @Parameter(description = "租户 UUID") @PathVariable UUID tenantId,
        @Parameter(description = "配置分类") @RequestParam(required = false) String category,
        @Parameter(description = "配置值类型") @RequestParam(required = false) SettingValueType valueType,
        @Parameter(description = "是否敏感配置") @RequestParam(required = false) Boolean sensitive,
        @Parameter(description = "关键字，匹配配置键、名称或值") @RequestParam(required = false) String keyword
    ) {
        return tenants.listSettings(tenantId, category, valueType, sensitive, keyword);
    }

    /**
     * 查询单个租户配置。
     */
    @Operation(summary = "获取租户配置", description = "根据租户 UUID 和配置键返回租户级配置。")
    @GetMapping("/{tenantId}/settings/{settingKey}")
    TenantSettingResponse setting(
        @Parameter(description = "租户 UUID") @PathVariable UUID tenantId,
        @Parameter(description = "配置键") @PathVariable String settingKey
    ) {
        return tenants.getSetting(tenantId, settingKey);
    }

    /**
     * 新增或更新租户配置。
     */
    @Operation(summary = "保存租户配置", description = "按配置键新增或更新租户级配置。")
    @PostMapping("/{tenantId}/settings")
    TenantSettingResponse upsertSetting(
        @Parameter(description = "租户 UUID") @PathVariable UUID tenantId,
        @Parameter(description = "租户配置保存请求") @Valid @RequestBody UpsertTenantSettingRequest request,
        Principal principal
    ) {
        return tenants.upsertSetting(tenantId, request, principal.getName());
    }

    /**
     * 删除租户配置。
     */
    @Operation(summary = "删除租户配置", description = "删除指定租户下的配置项。")
    @DeleteMapping("/{tenantId}/settings/{settingKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSetting(
        @Parameter(description = "租户 UUID") @PathVariable UUID tenantId,
        @Parameter(description = "配置键") @PathVariable String settingKey,
        Principal principal
    ) {
        tenants.deleteSetting(tenantId, settingKey, principal.getName());
    }
}
