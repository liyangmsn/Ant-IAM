package com.antiam.service;

import static com.antiam.dto.IdentitySourceDtos.CreateIdentitySourceRequest;
import static com.antiam.dto.IdentitySourceDtos.ConfigureConnectorRequest;
import static com.antiam.dto.IdentitySourceDtos.ConnectorResponse;
import static com.antiam.dto.IdentitySourceDtos.CreateSyncJobRequest;
import static com.antiam.dto.IdentitySourceDtos.IdentitySourceResponse;
import static com.antiam.dto.IdentitySourceDtos.SyncJobResponse;
import static com.antiam.dto.IdentitySourceDtos.SyncRunResponse;
import static com.antiam.dto.IdentitySourceDtos.UpdateIdentitySourceRequest;
import static com.antiam.dto.IdentitySourceDtos.UpdateSyncJobRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.domain.IdentitySyncJob;
import com.antiam.domain.IdentitySyncRun;
import com.antiam.domain.Organization;
import com.antiam.domain.Tenant;
import com.antiam.domain.UserAccount;
import com.antiam.domain.UserGroup;
import com.antiam.mapper.IdentitySourceMapper;
import com.antiam.repository.IdentitySourceConnectorRepository;
import com.antiam.repository.IdentitySourceRepository;
import com.antiam.repository.IdentitySyncJobRepository;
import com.antiam.repository.IdentitySyncRunRepository;
import com.antiam.repository.OrganizationRepository;
import com.antiam.repository.UserAccountRepository;
import com.antiam.repository.UserGroupRepository;
import com.antiam.service.identitysource.DirectoryGroup;
import com.antiam.service.identitysource.DirectoryOrganization;
import com.antiam.service.identitysource.DirectorySyncPayload;
import com.antiam.service.identitysource.DirectoryUser;
import com.antiam.service.identitysource.IdentitySourceConnectorAdapter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentitySourceService {

    private final IdentitySourceRepository identitySources;
    private final IdentitySourceConnectorRepository connectors;
    private final IdentitySyncJobRepository syncJobs;
    private final IdentitySyncRunRepository syncRuns;
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final UserGroupRepository groups;
    private final IdentitySourceMapper identitySourceMapper;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final List<IdentitySourceConnectorAdapter> connectorAdapters;

    @Transactional
    // 创建身份源，后续可配置连接器并通过同步任务导入组织、用户和用户组。
    public IdentitySourceResponse create(CreateIdentitySourceRequest request, String actor) {
        Tenant tenant = request.tenantId() == null ? null : tenantService.getEntity(request.tenantId());
        IdentitySource saved = identitySources.save(new IdentitySource(request.code(), request.name(), request.description(), request.type(), tenant));
        auditService.record(actor, "identity_source.create", "identity_source", saved.getId().toString(), saved.getCode());
        return identitySourceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询身份源列表，支持租户、类型、启用状态和关键字过滤。
    public List<IdentitySourceResponse> list(UUID tenantId, IdentitySourceType type, Boolean enabled, String keyword) {
        List<IdentitySource> values = tenantId == null ? identitySources.findAll() : identitySources.findByTenantId(tenantId);
        String normalizedKeyword = normalizeKeyword(keyword);
        return values.stream()
            .filter(source -> type == null || source.getType() == type)
            .filter(source -> enabled == null || source.isEnabled() == enabled)
            .filter(source -> normalizedKeyword == null || matchesKeyword(source, normalizedKeyword))
            .map(identitySourceMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询身份源详情。
    public IdentitySourceResponse get(UUID identitySourceId) {
        return identitySourceMapper.toResponse(getSource(identitySourceId));
    }

    @Transactional
    // 更新身份源展示信息，身份源编码和类型保持稳定。
    public IdentitySourceResponse update(UUID identitySourceId, UpdateIdentitySourceRequest request, String actor) {
        IdentitySource source = getSource(identitySourceId);
        source.update(request.name(), request.description());
        auditService.record(actor, "identity_source.update", "identity_source", identitySourceId.toString(), source.getCode());
        return identitySourceMapper.toResponse(source);
    }

    @Transactional
    // 启用身份源，使其可以参与同步任务。
    public IdentitySourceResponse enable(UUID identitySourceId, String actor) {
        IdentitySource source = getSource(identitySourceId);
        source.enable();
        auditService.record(actor, "identity_source.enable", "identity_source", identitySourceId.toString(), source.getCode());
        return identitySourceMapper.toResponse(source);
    }

    @Transactional
    // 停用身份源，阻止后续同步导入。
    public IdentitySourceResponse disable(UUID identitySourceId, String actor) {
        IdentitySource source = getSource(identitySourceId);
        source.disable();
        auditService.record(actor, "identity_source.disable", "identity_source", identitySourceId.toString(), source.getCode());
        return identitySourceMapper.toResponse(source);
    }

    @Transactional
    // 删除身份源，数据库外键会级联清理连接器、同步任务和运行历史。
    public void delete(UUID identitySourceId, String actor) {
        IdentitySource source = getSource(identitySourceId);
        String code = source.getCode();
        identitySources.delete(source);
        auditService.record(actor, "identity_source.delete", "identity_source", identitySourceId.toString(), code);
    }

    @Transactional
    // 保存身份源连接器配置，包含连接参数和密钥引用。
    public ConnectorResponse configureConnector(UUID identitySourceId, ConfigureConnectorRequest request, String actor) {
        IdentitySource source = getSource(identitySourceId);
        IdentitySourceConnector saved = connectors.findByIdentitySourceId(identitySourceId)
            .map(existing -> {
                existing.replace(request.configuration(), request.secretRef());
                return existing;
            })
            .orElseGet(() -> connectors.save(new IdentitySourceConnector(source, request.configuration(), request.secretRef())));
        auditService.record(actor, "identity_source.connector.configure", "identity_source", identitySourceId.toString(), source.getCode());
        return identitySourceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询身份源连接器配置。
    public ConnectorResponse getConnector(UUID identitySourceId) {
        return identitySourceMapper.toResponse(getConnectorEntity(identitySourceId));
    }

    @Transactional
    // 启用身份源连接器。
    public ConnectorResponse enableConnector(UUID identitySourceId, String actor) {
        IdentitySourceConnector connector = getConnectorEntity(identitySourceId);
        connector.enable();
        auditService.record(actor, "identity_source.connector.enable", "identity_source", identitySourceId.toString(), connector.getIdentitySource().getCode());
        return identitySourceMapper.toResponse(connector);
    }

    @Transactional
    // 停用身份源连接器。
    public ConnectorResponse disableConnector(UUID identitySourceId, String actor) {
        IdentitySourceConnector connector = getConnectorEntity(identitySourceId);
        connector.disable();
        auditService.record(actor, "identity_source.connector.disable", "identity_source", identitySourceId.toString(), connector.getIdentitySource().getCode());
        return identitySourceMapper.toResponse(connector);
    }

    @Transactional
    // 创建身份同步任务，保存同步模式和调度表达式。
    public SyncJobResponse createSyncJob(UUID identitySourceId, CreateSyncJobRequest request, String actor) {
        IdentitySource source = getSource(identitySourceId);
        IdentitySyncJob saved = syncJobs.save(new IdentitySyncJob(source, request.name(), request.mode(), request.cronExpression()));
        auditService.record(actor, "identity_sync_job.create", "identity_source", identitySourceId.toString(), saved.getName());
        return identitySourceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询指定身份源下的同步任务。
    public List<SyncJobResponse> listSyncJobs(UUID identitySourceId) {
        getSource(identitySourceId);
        return syncJobs.findByIdentitySourceId(identitySourceId).stream().map(identitySourceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 查询同步任务详情。
    public SyncJobResponse getSyncJob(UUID syncJobId) {
        return identitySourceMapper.toResponse(getSyncJobEntity(syncJobId));
    }

    @Transactional
    // 更新同步任务名称、模式和调度表达式。
    public SyncJobResponse updateSyncJob(UUID syncJobId, UpdateSyncJobRequest request, String actor) {
        IdentitySyncJob job = getSyncJobEntity(syncJobId);
        job.update(request.name(), request.mode(), request.cronExpression());
        auditService.record(actor, "identity_sync_job.update", "identity_sync_job", syncJobId.toString(), job.getName());
        return identitySourceMapper.toResponse(job);
    }

    @Transactional
    // 启用同步任务，使其可被调度或手动运行。
    public SyncJobResponse enableSyncJob(UUID syncJobId, String actor) {
        IdentitySyncJob job = getSyncJobEntity(syncJobId);
        job.enable();
        auditService.record(actor, "identity_sync_job.enable", "identity_sync_job", syncJobId.toString(), job.getName());
        return identitySourceMapper.toResponse(job);
    }

    @Transactional
    // 停用同步任务，保留历史运行记录。
    public SyncJobResponse disableSyncJob(UUID syncJobId, String actor) {
        IdentitySyncJob job = getSyncJobEntity(syncJobId);
        job.disable();
        auditService.record(actor, "identity_sync_job.disable", "identity_sync_job", syncJobId.toString(), job.getName());
        return identitySourceMapper.toResponse(job);
    }

    @Transactional
    // 立即运行同步任务，将连接器 JSON 数据应用到组织、用户和用户组目录。
    public SyncRunResponse runSyncJob(UUID syncJobId, String actor) {
        IdentitySyncJob job = getSyncJobEntity(syncJobId);
        if (!job.isEnabled()) {
            throw new IllegalArgumentException("Identity sync job is disabled: " + job.getName());
        }
        IdentitySyncRun run = syncRuns.save(new IdentitySyncRun(job));
        try {
            IdentitySourceConnector connector = connectors.findByIdentitySourceId(job.getIdentitySource().getId())
                .orElseThrow(() -> new NotFoundException("Connector not configured for identity source: " + job.getIdentitySource().getCode()));
            SyncCounters counters = applyConnectorSync(job.getIdentitySource(), connector);
            String message = "Applied " + job.getMode() + " sync for " + job.getIdentitySource().getType()
                + ": organizationsCreated=" + counters.organizationsCreated()
                + ", organizationsUpdated=" + counters.organizationsUpdated()
                + ", usersCreated=" + counters.usersCreated()
                + ", usersUpdated=" + counters.usersUpdated()
                + ", groupsCreated=" + counters.groupsCreated()
                + ", groupsUpdated=" + counters.groupsUpdated();
            run.success(counters.usersCreated(), counters.usersUpdated(), counters.groupsCreated(), counters.groupsUpdated(), message);
            auditService.record(actor, "identity_sync_job.run", "identity_sync_job", syncJobId.toString(), "success");
        } catch (RuntimeException ex) {
            run.fail(ex.getMessage());
            auditService.record(actor, "identity_sync_job.run", "identity_sync_job", syncJobId.toString(), "failed");
        }
        return identitySourceMapper.toResponse(run);
    }

    @Transactional(readOnly = true)
    // 查询同步任务最近运行记录。
    public List<SyncRunResponse> listSyncRuns(UUID syncJobId) {
        getSyncJobEntity(syncJobId);
        return syncRuns.findTop50BySyncJobIdOrderByCreatedAtDesc(syncJobId).stream().map(identitySourceMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 查询单次同步运行详情。
    public SyncRunResponse getSyncRun(UUID syncRunId) {
        return syncRuns.findById(syncRunId)
            .map(identitySourceMapper::toResponse)
            .orElseThrow(() -> new NotFoundException("Identity sync run not found: " + syncRunId));
    }

    private IdentitySource getSource(UUID identitySourceId) {
        return identitySources.findById(identitySourceId)
            .orElseThrow(() -> new NotFoundException("Identity source not found: " + identitySourceId));
    }

    private IdentitySyncJob getSyncJobEntity(UUID syncJobId) {
        return syncJobs.findById(syncJobId)
            .orElseThrow(() -> new NotFoundException("Identity sync job not found: " + syncJobId));
    }

    private IdentitySourceConnector getConnectorEntity(UUID identitySourceId) {
        return connectors.findByIdentitySourceId(identitySourceId)
            .orElseThrow(() -> new NotFoundException("Identity source connector not found: " + identitySourceId));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesKeyword(IdentitySource source, String keyword) {
        return contains(source.getCode(), keyword)
            || contains(source.getName(), keyword)
            || contains(source.getDescription(), keyword)
            || source.getType().name().toLowerCase().contains(keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private SyncCounters applyConnectorSync(IdentitySource source, IdentitySourceConnector connector) {
        if (!source.isEnabled()) {
            throw new IllegalArgumentException("Identity source is disabled: " + source.getCode());
        }
        if (!connector.isEnabled()) {
            throw new IllegalArgumentException("Identity source connector is disabled: " + source.getCode());
        }
        DirectorySyncPayload payload = connectorAdapters.stream()
            .filter(adapter -> adapter.supports(source.getType()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported identity source connector type: " + source.getType()))
            .load(source, connector);
        SyncCounters counters = new SyncCounters();
        payload.organizations().forEach(item -> syncOrganization(item, counters));
        payload.users().forEach(item -> syncUser(source, item, counters));
        payload.groups().forEach(item -> syncGroup(item, counters));
        return counters;
    }

    private void syncOrganization(DirectoryOrganization item, SyncCounters counters) {
        Organization parent = item.parentCode() == null || item.parentCode().isBlank()
            ? null
            : organizations.findByCode(item.parentCode())
                .orElseThrow(() -> new NotFoundException("Parent organization not found: " + item.parentCode()));
        organizations.findByCode(required(item.code(), "organization.code"))
            .map(existing -> {
                existing.update(required(item.name(), "organization.name"), parent);
                counters.organizationsUpdated++;
                return existing;
            })
            .orElseGet(() -> {
                counters.organizationsCreated++;
                return organizations.save(new Organization(item.code(), required(item.name(), "organization.name"), parent));
            });
    }

    private void syncUser(IdentitySource source, DirectoryUser item, SyncCounters counters) {
        Organization organization = item.organizationCode() == null || item.organizationCode().isBlank()
            ? null
            : organizations.findByCode(item.organizationCode())
                .orElseThrow(() -> new NotFoundException("Organization not found: " + item.organizationCode()));
        users.findByUsername(required(item.username(), "user.username"))
            .map(existing -> {
                existing.updateProfile(displayName(item), item.email(), item.mobile(), organization);
                counters.usersUpdated++;
                return existing;
            })
            .orElseGet(() -> {
                counters.usersCreated++;
                return users.save(new UserAccount(
                    item.username(),
                    displayName(item),
                    item.email(),
                    item.mobile(),
                    source.getTenant(),
                    organization));
            });
    }

    private void syncGroup(DirectoryGroup item, SyncCounters counters) {
        UserGroup group = groups.findByCode(required(item.code(), "group.code"))
            .map(existing -> {
                existing.rename(required(item.name(), "group.name"));
                counters.groupsUpdated++;
                return existing;
            })
            .orElseGet(() -> {
                counters.groupsCreated++;
                return groups.save(new UserGroup(item.code(), required(item.name(), "group.name")));
            });
        if (item.members() != null) {
            item.members().forEach(username -> users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Group member user not found: " + username))
                .join(group));
        }
    }

    private String displayName(DirectoryUser item) {
        if (item.displayName() != null && !item.displayName().isBlank()) {
            return item.displayName();
        }
        return required(item.username(), "user.username");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static final class SyncCounters {
        private int organizationsCreated;
        private int organizationsUpdated;
        private int usersCreated;
        private int usersUpdated;
        private int groupsCreated;
        private int groupsUpdated;

        int organizationsCreated() {
            return organizationsCreated;
        }

        int organizationsUpdated() {
            return organizationsUpdated;
        }

        int usersCreated() {
            return usersCreated;
        }

        int usersUpdated() {
            return usersUpdated;
        }

        int groupsCreated() {
            return groupsCreated;
        }

        int groupsUpdated() {
            return groupsUpdated;
        }
    }
}
