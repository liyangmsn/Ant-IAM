package com.antiam.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ObjectStorageAdapter implements ObjectStorageAdapter {

    private static final String S3_PROVIDER = "s3";
    private static final String MINIO_PROVIDER = "minio";
    private static final String MINIO_DEFAULT_REGION = "us-east-1";

    @Override
    public boolean supports(String provider) {
        return S3_PROVIDER.equals(provider) || MINIO_PROVIDER.equals(provider);
    }

    @Override
    public StoredFile store(JsonNode config, String objectKey, String contentType, byte[] content) {
        String provider = StorageConfig.required(config, S3_PROVIDER, "provider");
        String endpoint = StorageConfig.required(config, provider, "endpoint");
        String bucket = StorageConfig.required(config, provider, "bucket");
        boolean minio = MINIO_PROVIDER.equals(provider);
        String accessKey = StorageConfig.required(config, provider, minio ? "accessKey" : "accessKeyId");
        String secretKey = StorageConfig.required(config, provider, minio ? "secretKey" : "secretAccessKey");
        try (S3Client client = S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region(config, provider)))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            .build()) {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(content));
        }
        return new StoredFile(objectKey, publicUrl(config, provider, endpoint, bucket) + "/" + objectKey, content.length);
    }

    private String region(JsonNode config, String provider) {
        String region = StorageConfig.text(config, "region");
        if (region != null) {
            return region;
        }
        if (MINIO_PROVIDER.equals(provider)) {
            return MINIO_DEFAULT_REGION;
        }
        throw new IllegalArgumentException(provider + " storage field is required: region");
    }

    /**
     * MinIO 直接使用服务地址访问；S3 兼容服务按控制台配置的外链域名访问。
     */
    private String publicUrl(JsonNode config, String provider, String endpoint, String bucket) {
        if (MINIO_PROVIDER.equals(provider)) {
            return StorageConfig.stripTrailingSlash(endpoint) + "/" + bucket;
        }
        return StorageConfig.publicUrl(config, provider);
    }
}
