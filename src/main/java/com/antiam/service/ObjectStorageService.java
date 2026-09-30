package com.antiam.service;

import com.antiam.dto.SettingDtos.IntegrationTestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final SettingJsonSupport settings;

    public IntegrationTestResponse validateCurrentConfiguration() {
        JsonNode config = settings.json("storage.provider");
        if (!config.path("enabled").asBoolean(false)) {
            throw new IllegalArgumentException("Object storage is disabled");
        }
        String provider = text(config, "provider", "local").toLowerCase(Locale.ROOT);
        if ("local".equals(provider)) {
            validateLocal(config);
            return new IntegrationTestResponse(true, "本地对象存储目录可写");
        }
        if ("minio".equals(provider) || "s3".equals(provider)) {
            validateS3Compatible(provider, config);
            return new IntegrationTestResponse(true, provider + " 对象存储连接成功");
        }
        validateRemote(provider, config);
        return new IntegrationTestResponse(true, provider + " 对象存储配置字段完整");
    }

    private void validateLocal(JsonNode config) {
        String rootPath = required(config, "rootPath");
        try {
            Path root = Path.of(rootPath).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path probe = root.resolve(".antiam-storage-probe");
            Files.writeString(probe, "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probe);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Local object storage is not writable: " + ex.getMessage(), ex);
        }
    }

    private void validateRemote(String provider, JsonNode config) {
        switch (provider) {
            case "minio", "s3" -> validateS3Compatible(provider, config);
            case "aliyun", "oss" -> {
                required(config, "accessKeyId");
                required(config, "accessKeySecret");
                required(config, "endpoint");
                required(config, "bucket");
            }
            case "tencent" -> {
                required(config, "secretId");
                required(config, "secretKey");
                required(config, "region");
                required(config, "bucket");
            }
            case "qiniu" -> {
                required(config, "accessKey");
                required(config, "secretKey");
                required(config, "bucket");
            }
            default -> throw new IllegalArgumentException("Unsupported object storage provider: " + provider);
        }
    }

    private void validateS3Compatible(String provider, JsonNode config) {
        String accessKey = requiredAny(config, "accessKey", "accessKeyId");
        String secretKey = requiredAny(config, "secretKey", "secretAccessKey");
        String endpoint = required(config, "endpoint");
        String bucket = required(config, "bucket");
        String region = text(config, "region", "us-east-1");
        try (S3Client client = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            // 与上传保持一致使用 path-style，自建 S3 兼容服务（RustFS/MinIO）的内网地址不支持 bucket 子域名
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
            client.headBucket(request -> request.bucket(bucket));
        } catch (Exception ex) {
            throw new IllegalArgumentException(provider + " object storage connection failed: " + ex.getMessage(), ex);
        }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Object storage field is required: " + field);
        }
        return value;
    }

    private String requiredAny(JsonNode node, String first, String second) {
        String value = text(node, first, "");
        if (!value.isBlank()) {
            return value;
        }
        return required(node, second);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? defaultValue : value.asText(defaultValue);
    }
}
