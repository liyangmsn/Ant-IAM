package com.antiam.web;

import static com.antiam.dto.IdentitySourceDtos.CreateIdentitySourceRequest;
import static com.antiam.dto.IdentitySourceDtos.ConfigureConnectorRequest;
import static com.antiam.dto.IdentitySourceDtos.ConnectorResponse;
import static com.antiam.dto.IdentitySourceDtos.CreateSyncJobRequest;
import static com.antiam.dto.IdentitySourceDtos.IdentitySourceResponse;
import static com.antiam.dto.IdentitySourceDtos.SyncJobResponse;
import static com.antiam.dto.IdentitySourceDtos.SyncRunResponse;
import static com.antiam.dto.IdentitySourceDtos.UpdateIdentitySourceRequest;
import static com.antiam.dto.IdentitySourceDtos.UpdateSyncJobRequest;

import com.antiam.config.IssuerResolver;
import com.antiam.domain.IdentitySourceType;
import com.antiam.dto.ScimSourceDtos.SyncTokenResponse;
import com.antiam.dto.ScimSourceDtos.SyncTokenStatusResponse;
import com.antiam.service.IdentitySourceService;
import com.antiam.service.ScimIdentitySourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/identity-sources")
@RequiredArgsConstructor
@Tag(name = "身份源", description = "身份源、连接器配置和同步任务管理接口")
public class IdentitySourceController {

    private final IdentitySourceService identitySources;
    private final ScimIdentitySourceService scimSources;
    private final IssuerResolver issuerResolver;

    /**
     * 查询身份源列表。
     */
    @Operation(summary = "查询身份源", description = "查询企业微信、LDAP、飞书等身份源配置，支持租户、类型、启用状态和关键字过滤。")
    @GetMapping
    List<IdentitySourceResponse> list(
        @Parameter(description = "租户 UUID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "身份源类型") @RequestParam(required = false) IdentitySourceType type,
        @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled,
        @Parameter(description = "关键字，匹配身份源编码、名称或配置") @RequestParam(required = false) String keyword
    ) {
        return identitySources.list(tenantId, type, enabled, keyword);
    }

    /**
     * 创建身份源。
     */
    @Operation(summary = "创建身份源", description = "创建新的外部身份源配置。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IdentitySourceResponse create(
        @Parameter(description = "身份源创建请求") @Valid @RequestBody CreateIdentitySourceRequest request,
        Principal principal
    ) {
        return identitySources.create(request, principal.getName());
    }

    /**
     * 查询身份源详情。
     */
    @Operation(summary = "获取身份源详情", description = "根据身份源 UUID 返回身份源详情。")
    @GetMapping("/{identitySourceId}")
    IdentitySourceResponse get(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId) {
        return identitySources.get(identitySourceId);
    }

    /**
     * 更新身份源配置。
     */
    @Operation(summary = "更新身份源", description = "更新身份源名称、类型、租户归属和基础配置。")
    @PutMapping("/{identitySourceId}")
    IdentitySourceResponse update(
        @Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId,
        @Parameter(description = "身份源更新请求") @Valid @RequestBody UpdateIdentitySourceRequest request,
        Principal principal
    ) {
        return identitySources.update(identitySourceId, request, principal.getName());
    }

    /**
     * 启用身份源。
     */
    @Operation(summary = "启用身份源", description = "启用身份源，使其可参与同步任务。")
    @PostMapping("/{identitySourceId}/enable")
    IdentitySourceResponse enable(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        return identitySources.enable(identitySourceId, principal.getName());
    }

    /**
     * 停用身份源。
     */
    @Operation(summary = "停用身份源", description = "停用身份源，阻止后续同步运行。")
    @PostMapping("/{identitySourceId}/disable")
    IdentitySourceResponse disable(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        return identitySources.disable(identitySourceId, principal.getName());
    }

    /**
     * 删除身份源。
     */
    @Operation(summary = "删除身份源", description = "删除身份源，并级联清理连接器、同步任务和同步运行历史。")
    @DeleteMapping("/{identitySourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        identitySources.delete(identitySourceId, principal.getName());
    }

    /**
     * 查询身份源连接器配置。
     */
    @Operation(summary = "获取连接器配置", description = "返回身份源连接器的端点、凭据摘要和启用状态。")
    @GetMapping("/{identitySourceId}/connector")
    ConnectorResponse connector(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId) {
        return identitySources.getConnector(identitySourceId);
    }

    /**
     * 配置身份源连接器。
     */
    @Operation(summary = "配置连接器", description = "为身份源配置连接地址、认证方式和连接参数。")
    @PostMapping("/{identitySourceId}/connector")
    ConnectorResponse configureConnector(
        @Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId,
        @Parameter(description = "连接器配置请求") @RequestBody ConfigureConnectorRequest request,
        Principal principal
    ) {
        return identitySources.configureConnector(identitySourceId, request, principal.getName());
    }

    /**
     * 启用身份源连接器。
     */
    @Operation(summary = "启用连接器", description = "启用身份源连接器。")
    @PostMapping("/{identitySourceId}/connector/enable")
    ConnectorResponse enableConnector(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        return identitySources.enableConnector(identitySourceId, principal.getName());
    }

    /**
     * 停用身份源连接器。
     */
    @Operation(summary = "停用连接器", description = "停用身份源连接器。")
    @PostMapping("/{identitySourceId}/connector/disable")
    ConnectorResponse disableConnector(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        return identitySources.disableConnector(identitySourceId, principal.getName());
    }

    /**
     * 查询通用 SCIM 身份源的同步令牌状态与推送概况。
     */
    @Operation(summary = "查询 SCIM 同步令牌状态", description = "返回是否已生成同步令牌、SCIM 基础地址、最近推送时间以及本源的组织、用户、用户组数量。")
    @GetMapping("/{identitySourceId}/scim-token")
    SyncTokenStatusResponse scimTokenStatus(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, HttpServletRequest request) {
        return scimSources.tokenStatus(identitySourceId, issuerResolver.resolve(request));
    }

    /**
     * 生成或重新生成通用 SCIM 身份源的同步令牌。
     */
    @Operation(summary = "生成 SCIM 同步令牌", description = "生成新的同步令牌，旧令牌立即失效；明文只在本次响应中返回。")
    @PostMapping("/{identitySourceId}/scim-token")
    SyncTokenResponse issueScimToken(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, HttpServletRequest request, Principal principal) {
        return scimSources.issueToken(identitySourceId, issuerResolver.resolve(request), principal.getName());
    }

    /**
     * 吊销通用 SCIM 身份源的同步令牌。
     */
    @Operation(summary = "吊销 SCIM 同步令牌", description = "吊销后第三方系统无法继续推送，需重新生成令牌。")
    @DeleteMapping("/{identitySourceId}/scim-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeScimToken(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId, Principal principal) {
        scimSources.revokeToken(identitySourceId, principal.getName());
    }

    /**
     * 查询身份源同步任务。
     */
    @Operation(summary = "查询同步任务", description = "返回指定身份源下的同步任务列表。")
    @GetMapping("/{identitySourceId}/sync-jobs")
    List<SyncJobResponse> syncJobs(@Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId) {
        return identitySources.listSyncJobs(identitySourceId);
    }

    /**
     * 创建身份源同步任务。
     */
    @Operation(summary = "创建同步任务", description = "为身份源创建定时或手动同步任务。")
    @PostMapping("/{identitySourceId}/sync-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    SyncJobResponse createSyncJob(
        @Parameter(description = "身份源 UUID") @PathVariable UUID identitySourceId,
        @Parameter(description = "同步任务创建请求") @Valid @RequestBody CreateSyncJobRequest request,
        Principal principal
    ) {
        return identitySources.createSyncJob(identitySourceId, request, principal.getName());
    }

    /**
     * 触发同步任务运行。
     */
    @Operation(summary = "运行同步任务", description = "立即触发指定同步任务运行，并记录运行结果。")
    @PostMapping("/sync-jobs/{syncJobId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    SyncRunResponse runSyncJob(@Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId, Principal principal) {
        return identitySources.runSyncJob(syncJobId, principal.getName());
    }

    /**
     * 查询同步任务详情。
     */
    @Operation(summary = "获取同步任务详情", description = "根据同步任务 UUID 返回同步任务详情。")
    @GetMapping("/sync-jobs/{syncJobId}")
    SyncJobResponse syncJob(@Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId) {
        return identitySources.getSyncJob(syncJobId);
    }

    /**
     * 更新同步任务。
     */
    @Operation(summary = "更新同步任务", description = "更新同步任务名称、调度表达式和同步范围。")
    @PutMapping("/sync-jobs/{syncJobId}")
    SyncJobResponse updateSyncJob(
        @Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId,
        @Parameter(description = "同步任务更新请求") @Valid @RequestBody UpdateSyncJobRequest request,
        Principal principal
    ) {
        return identitySources.updateSyncJob(syncJobId, request, principal.getName());
    }

    /**
     * 启用同步任务。
     */
    @Operation(summary = "启用同步任务", description = "启用同步任务，使其可被调度或手动运行。")
    @PostMapping("/sync-jobs/{syncJobId}/enable")
    SyncJobResponse enableSyncJob(@Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId, Principal principal) {
        return identitySources.enableSyncJob(syncJobId, principal.getName());
    }

    /**
     * 停用同步任务。
     */
    @Operation(summary = "停用同步任务", description = "停用同步任务，阻止调度运行。")
    @PostMapping("/sync-jobs/{syncJobId}/disable")
    SyncJobResponse disableSyncJob(@Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId, Principal principal) {
        return identitySources.disableSyncJob(syncJobId, principal.getName());
    }

    /**
     * 查询同步任务运行记录。
     */
    @Operation(summary = "查询同步运行记录", description = "返回指定同步任务的运行历史。")
    @GetMapping("/sync-jobs/{syncJobId}/runs")
    List<SyncRunResponse> syncRuns(@Parameter(description = "同步任务 UUID") @PathVariable UUID syncJobId) {
        return identitySources.listSyncRuns(syncJobId);
    }

    /**
     * 查询单次同步运行详情。
     */
    @Operation(summary = "获取同步运行详情", description = "根据同步运行 UUID 返回运行结果、耗时和统计。")
    @GetMapping("/sync-runs/{syncRunId}")
    SyncRunResponse syncRun(@Parameter(description = "同步运行 UUID") @PathVariable UUID syncRunId) {
        return identitySources.getSyncRun(syncRunId);
    }
}
