package com.antiam.service.storage;

import com.antiam.common.ServiceUnavailableException;
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
    private static final String PROBE_OBJECT_KEY = "uploads/.ant-iam-probe.txt";

    private final SystemSettingRepository settings;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final List<ObjectStorageAdapter> storageAdapters;

    /**
     * 将文件写入已配置的对象存储，并返回可访问地址。
     */
    public StoredFile store(String originalFilename, String contentType, byte[] content, String actor) {
        return store(originalFilename, contentType, content, objectKey(originalFilename), actor);
    }

    private StoredFile store(String originalFilename, String contentType, byte[] content, String objectKey, String actor) {
        JsonNode config = storageConfig();
        String provider = StorageConfig.required(config, "storage", "provider");
        ObjectStorageAdapter adapter = storageAdapters.stream()
            .filter(candidate -> candidate.supports(provider))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported object storage provider: " + provider));
        StoredFile stored = adapter.store(config, objectKey, contentType, content);
        auditService.record(actor, "file.upload", "file", stored.objectKey(), provider);
        return stored;
    }

    /**
     * 写入一个固定的探测文件验证对象存储配置可用，重复校验会覆盖同一对象。
     */
    public String validate(String actor) {
        try {
            StoredFile stored = store("probe.txt", "text/plain", "ant-iam storage probe".getBytes(java.nio.charset.StandardCharsets.UTF_8), PROBE_OBJECT_KEY, actor);
            return "对象存储校验通过，探测文件：" + stored.url();
        } catch (IllegalStateException | ServiceUnavailableException ex) {
            throw new IllegalArgumentException("对象存储校验失败：" + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) {
                throw ex;
            }
            throw new IllegalArgumentException("对象存储校验失败：" + ex.getMessage(), ex);
        }
    }

    private JsonNode storageConfig() {
        SystemSetting setting = settings.findBySettingKey(SETTING_KEY)
            .orElseThrow(() -> new ServiceUnavailableException("对象存储未配置，请先在系统设置中配置存储服务"));
        try {
            JsonNode config = objectMapper.readTree(setting.getSettingValue());
            if (!config.path("enabled").asBoolean(false)) {
                throw new ServiceUnavailableException("对象存储未启用，请先在系统设置中启用存储服务");
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new ServiceUnavailableException("对象存储配置不是合法 JSON", ex);
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
