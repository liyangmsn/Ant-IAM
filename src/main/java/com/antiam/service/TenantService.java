package com.antiam.service;

import static com.antiam.dto.TenantDtos.CreateTenantRequest;
import static com.antiam.dto.TenantDtos.TenantResponse;
import static com.antiam.dto.TenantDtos.TenantSettingResponse;
import static com.antiam.dto.TenantDtos.UpdateTenantRequest;
import static com.antiam.dto.TenantDtos.UpsertTenantSettingRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.SettingValueType;
import com.antiam.domain.Tenant;
import com.antiam.domain.TenantSetting;
import com.antiam.repository.TenantRepository;
import com.antiam.repository.TenantSettingRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenants;
    private final TenantSettingRepository settings;
    private final AuditService auditService;

    @Transactional
    // 创建租户空间，后续用户、组织、应用和身份源可归属到该租户。
    public TenantResponse create(CreateTenantRequest request, String actor) {
        Tenant saved = tenants.save(new Tenant(request.code(), request.name(), request.domain()));
        auditService.record(actor, "tenant.create", "tenant", saved.getId().toString(), saved.getCode());
        return toResponse(saved);
    }

    @Transactional
    // 更新租户基础信息，租户编码保持稳定。
    public TenantResponse update(UUID tenantId, UpdateTenantRequest request, String actor) {
        Tenant tenant = getEntity(tenantId);
        tenant.rename(request.name(), request.domain());
        auditService.record(actor, "tenant.update", "tenant", tenantId.toString(), tenant.getCode());
        return toResponse(tenant);
    }

    @Transactional
    // 激活租户，使租户重新可用。
    public TenantResponse activate(UUID tenantId, String actor) {
        Tenant tenant = getEntity(tenantId);
        tenant.activate();
        auditService.record(actor, "tenant.activate", "tenant", tenantId.toString(), tenant.getCode());
        return toResponse(tenant);
    }

    @Transactional
    // 暂停租户，用于临时阻断租户级访问。
    public TenantResponse suspend(UUID tenantId, String actor) {
        Tenant tenant = getEntity(tenantId);
        tenant.suspend();
        auditService.record(actor, "tenant.suspend", "tenant", tenantId.toString(), tenant.getCode());
        return toResponse(tenant);
    }

    @Transactional(readOnly = true)
    // 查询全部租户。
    public List<TenantResponse> list() {
        return tenants.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    // 查询租户详情。
    public TenantResponse get(UUID tenantId) {
        return toResponse(getEntity(tenantId));
    }

    @Transactional(readOnly = true)
    // 获取租户实体，供其他业务在绑定租户前校验租户存在。
    public Tenant getEntity(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional
    // 新增或更新租户级配置，敏感配置在响应中会脱敏。
    public TenantSettingResponse upsertSetting(UUID tenantId, UpsertTenantSettingRequest request, String actor) {
        Tenant tenant = getEntity(tenantId);
        TenantSetting saved = settings.findByTenantIdAndSettingKey(tenantId, request.settingKey())
            .map(existing -> {
                existing.update(request.valueType(), request.settingValue(), request.description(), request.sensitive());
                return existing;
            })
            .orElseGet(() -> settings.save(new TenantSetting(
                tenant,
                request.settingKey(),
                request.category(),
                request.valueType(),
                request.settingValue(),
                request.description(),
                request.sensitive())));
        auditService.record(actor, "tenant_setting.upsert", "tenant", tenantId.toString(), saved.getSettingKey());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询租户级配置，支持分类、值类型、敏感标记和关键字过滤。
    public List<TenantSettingResponse> listSettings(
        UUID tenantId,
        String category,
        SettingValueType valueType,
        Boolean sensitive,
        String keyword
    ) {
        getEntity(tenantId);
        List<TenantSetting> values = category == null || category.isBlank()
            ? settings.findByTenantIdOrderBySettingKeyAsc(tenantId)
            : settings.findByTenantIdAndCategoryOrderBySettingKeyAsc(tenantId, category);
        String normalizedKeyword = normalizeKeyword(keyword);
        return values.stream()
            .filter(setting -> valueType == null || setting.getValueType() == valueType)
            .filter(setting -> sensitive == null || setting.isSensitive() == sensitive)
            .filter(setting -> normalizedKeyword == null || matchesKeyword(setting, normalizedKeyword))
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询单个租户级配置。
    public TenantSettingResponse getSetting(UUID tenantId, String settingKey) {
        return settings.findByTenantIdAndSettingKey(tenantId, settingKey)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("Tenant setting not found: " + settingKey));
    }

    @Transactional
    // 删除租户级配置项。
    public void deleteSetting(UUID tenantId, String settingKey, String actor) {
        TenantSetting setting = settings.findByTenantIdAndSettingKey(tenantId, settingKey)
            .orElseThrow(() -> new NotFoundException("Tenant setting not found: " + settingKey));
        settings.delete(setting);
        auditService.record(actor, "tenant_setting.delete", "tenant", tenantId.toString(), setting.getSettingKey());
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.getDomain(), tenant.getStatus());
    }

    private TenantSettingResponse toResponse(TenantSetting setting) {
        String value = setting.isSensitive() && setting.getSettingValue() != null ? "******" : setting.getSettingValue();
        return new TenantSettingResponse(
            setting.getId(),
            setting.getTenant().getId(),
            setting.getSettingKey(),
            setting.getCategory(),
            setting.getValueType(),
            value,
            setting.getDescription(),
            setting.isSensitive());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesKeyword(TenantSetting setting, String keyword) {
        return contains(setting.getSettingKey(), keyword)
            || contains(setting.getCategory(), keyword)
            || contains(setting.getDescription(), keyword)
            || (!setting.isSensitive() && contains(setting.getSettingValue(), keyword))
            || setting.getValueType().name().toLowerCase().contains(keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
