package com.antiam.service.storage;

import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.antiam.service.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final String SETTING_KEY = "storage.provider";

    private final SystemSettingRepository settings;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final List<ObjectStorageAdapter> storageAdapters;

    /**
     * 将文件写入已配置的对象存储，并返回可访问地址。
     */
    public StoredFile store(String originalFilename, String contentType, byte[] content) {
        JsonNode config = storageConfig();
        String provider = StorageConfig.required(config, "storage", "provider");
        ObjectStorageAdapter adapter = storageAdapters.stream()
            .filter(candidate -> candidate.supports(provider))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported object storage provider: " + provider));
        StoredFile stored = adapter.store(config, objectKey(originalFilename), contentType, content);
        auditService.record("file-storage", "file.upload", "file", stored.objectKey(), provider);
        return stored;
    }

    private JsonNode storageConfig() {
        SystemSetting setting = settings.findBySettingKey(SETTING_KEY)
            .orElseThrow(() -> new IllegalStateException("Storage provider is not configured: " + SETTING_KEY));
        try {
            JsonNode config = objectMapper.readTree(setting.getSettingValue());
            if (!config.path("enabled").asBoolean(false)) {
                throw new IllegalStateException("Storage provider is disabled: " + SETTING_KEY);
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Storage setting value must be valid JSON", ex);
        }
    }

    private String objectKey(String originalFilename) {
        String extension = "";
        int dot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        if (dot >= 0) {
            extension = originalFilename.substring(dot).toLowerCase();
        }
        return "uploads/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
    }
}
