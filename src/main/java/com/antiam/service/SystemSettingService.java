package com.antiam.service;

import static com.antiam.dto.SettingDtos.SettingResponse;
import static com.antiam.dto.SettingDtos.UpsertSettingRequest;

import com.antiam.common.NotFoundException;
import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.mapper.SettingMapper;
import com.antiam.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository settings;
    private final SettingMapper settingMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    // 新增或更新全局系统配置，敏感配置在响应中会脱敏。
    public SettingResponse upsert(UpsertSettingRequest request, String actor) {
        SystemSetting saved = settings.findBySettingKey(request.settingKey())
            .map(existing -> {
                existing.update(request.valueType(), mergeMaskedJson(existing, request), request.description(), request.sensitive());
                return existing;
            })
            .orElseGet(() -> settings.save(new SystemSetting(
                request.settingKey(),
                request.category(),
                request.valueType(),
                request.settingValue(),
                request.description(),
                request.sensitive())));
        auditService.record(actor, "system_setting.upsert", "system_setting", saved.getId().toString(), saved.getSettingKey());
        return settingMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    // 查询全局系统配置，支持分类、值类型、敏感标记和关键字过滤。
    public List<SettingResponse> list(String category, SettingValueType valueType, Boolean sensitive, String keyword) {
        List<SystemSetting> values = category == null || category.isBlank()
            ? settings.findAll(Sort.by(Sort.Direction.ASC, "settingKey"))
            : settings.findByCategoryOrderBySettingKeyAsc(category);
        String normalizedKeyword = normalizeKeyword(keyword);
        return values.stream()
            .filter(setting -> valueType == null || setting.getValueType() == valueType)
            .filter(setting -> sensitive == null || setting.isSensitive() == sensitive)
            .filter(setting -> normalizedKeyword == null || matchesKeyword(setting, normalizedKeyword))
            .map(settingMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    // 查询单个全局系统配置。
    public SettingResponse get(String settingKey) {
        return settings.findBySettingKey(settingKey)
            .map(settingMapper::toResponse)
            .orElseThrow(() -> new NotFoundException("System setting not found: " + settingKey));
    }

    @Transactional
    // 删除全局系统配置项。
    public void delete(String settingKey, String actor) {
        SystemSetting setting = settings.findBySettingKey(settingKey)
            .orElseThrow(() -> new NotFoundException("System setting not found: " + settingKey));
        settings.delete(setting);
        auditService.record(actor, "system_setting.delete", "system_setting", setting.getId().toString(), setting.getSettingKey());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.toLowerCase();
    }

    private boolean matchesKeyword(SystemSetting setting, String keyword) {
        return contains(setting.getSettingKey(), keyword)
            || contains(setting.getCategory(), keyword)
            || contains(setting.getDescription(), keyword)
            || (!setting.isSensitive() && contains(setting.getSettingValue(), keyword))
            || setting.getValueType().name().toLowerCase().contains(keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String mergeMaskedJson(SystemSetting existing, UpsertSettingRequest request) {
        if (request.valueType() != SettingValueType.JSON || request.settingValue() == null || existing.getSettingValue() == null) {
            return request.settingValue();
        }
        try {
            JsonNode incoming = objectMapper.readTree(request.settingValue());
            JsonNode original = objectMapper.readTree(existing.getSettingValue());
            if (incoming instanceof ObjectNode incomingObject && original instanceof ObjectNode originalObject) {
                restoreMaskedValues(incomingObject, originalObject);
                return objectMapper.writeValueAsString(incomingObject);
            }
        } catch (Exception ignored) {
            return request.settingValue();
        }
        return request.settingValue();
    }

    private void restoreMaskedValues(ObjectNode incoming, ObjectNode original) {
        incoming.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            JsonNode originalValue = original.get(entry.getKey());
            if (value instanceof ObjectNode valueObject && originalValue instanceof ObjectNode originalObject) {
                restoreMaskedValues(valueObject, originalObject);
                return;
            }
            if (value.isTextual() && "******".equals(value.asText()) && originalValue != null) {
                incoming.set(entry.getKey(), originalValue);
            }
        });
    }
}
