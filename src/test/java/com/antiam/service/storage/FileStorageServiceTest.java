package com.antiam.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.antiam.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

class FileStorageServiceTest {

    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final FileStorageService service = new FileStorageService(
        settings,
        mock(AuditService.class),
        new ObjectMapper().findAndRegisterModules(),
        List.of(
            new AliyunObjectStorageAdapter(),
            new TencentObjectStorageAdapter(),
            new QiniuObjectStorageAdapter(),
            new S3ObjectStorageAdapter()));

    @Test
    void rejectsUploadWhenStorageIsNotConfigured() {
        when(settings.findBySettingKey("storage.provider")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Storage provider is not configured");
    }

    @Test
    void rejectsUploadWhenStorageIsDisabled() {
        configure("""
            {"provider": "minio", "endpoint": "https://minio.example.com", "accessKey": "key", "secretKey": "secret", "bucket": "assets", "enabled": false}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Storage provider is disabled");
    }

    @Test
    void rejectsProviderWithoutAdapter() {
        configure("""
            {"provider": "azure", "bucket": "assets", "enabled": true}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported object storage provider: azure");
    }

    @Test
    void aliyunAdapterIsSelectedForAliyunProvider() {
        configure("""
            {"provider": "aliyun", "accessKeyId": "key", "accessKeySecret": "secret", "bucket": "assets", "publicUrl": "https://cdn.example.com", "enabled": true}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("aliyun storage field is required: endpoint");
    }

    @Test
    void tencentAdapterRequiresAppId() {
        configure("""
            {"provider": "tencent", "secretId": "id", "secretKey": "secret", "region": "ap-shanghai", "bucket": "assets", "publicUrl": "https://cdn.example.com", "enabled": true}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tencent storage field is required: appId");
    }

    @Test
    void qiniuAdapterRequiresBucket() {
        configure("""
            {"provider": "qiniu", "accessKey": "key", "secretKey": "secret", "publicUrl": "https://cdn.example.com", "enabled": true}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("qiniu storage field is required: bucket");
    }

    @Test
    void s3AdapterRequiresRegion() {
        configure("""
            {"provider": "s3", "accessKeyId": "key", "secretAccessKey": "secret", "endpoint": "https://s3.example.com", "bucket": "assets", "publicUrl": "https://cdn.example.com", "enabled": true}
            """);

        assertThatThrownBy(() -> service.store("avatar.png", "image/png", new byte[]{1}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("s3 storage field is required: region");
    }

    @Test
    void minioAdapterDefaultsRegionAndDerivesUrlFromEndpoint() {
        S3ObjectStorageAdapter adapter = new S3ObjectStorageAdapter();
        String region = ReflectionTestUtils.invokeMethod(adapter, "region", new ObjectMapper().createObjectNode(), "minio");
        String url = ReflectionTestUtils.invokeMethod(adapter, "publicUrl",
            new ObjectMapper().createObjectNode(), "minio", "https://minio.example.com/", "assets");

        assertThat(region).isEqualTo("us-east-1");
        assertThat(url).isEqualTo("https://minio.example.com/assets");
    }

    @Test
    void buildsDatedObjectKeyWithLowercasedExtension() {
        String objectKey = ReflectionTestUtils.invokeMethod(service, "objectKey", "Avatar.PNG");

        assertThat(objectKey).startsWith("uploads/").endsWith(".png");
    }

    @Test
    void s3AdapterRequiresPublicUrlInsteadOfFallingBackToEndpoint() {
        S3ObjectStorageAdapter adapter = new S3ObjectStorageAdapter();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(adapter, "publicUrl",
            new ObjectMapper().createObjectNode(), "s3", "https://s3.example.com", "assets"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("s3 storage field is required: publicUrl");
    }

    private void configure(String value) {
        when(settings.findBySettingKey("storage.provider"))
            .thenReturn(Optional.of(new SystemSetting(
                "storage.provider", "storage", SettingValueType.JSON, value, "storage.provider", false)));
    }
}
