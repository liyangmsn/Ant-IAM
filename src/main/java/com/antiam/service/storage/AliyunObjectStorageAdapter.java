package com.antiam.service.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

@Component
public class AliyunObjectStorageAdapter implements ObjectStorageAdapter {

    private static final String PROVIDER = "aliyun";

    @Override
    public boolean supports(String provider) {
        return PROVIDER.equals(provider);
    }

    @Override
    public StoredFile store(JsonNode config, String objectKey, String contentType, byte[] content) {
        String endpoint = StorageConfig.required(config, PROVIDER, "endpoint");
        String bucket = StorageConfig.required(config, PROVIDER, "bucket");
        String accessKeyId = StorageConfig.required(config, PROVIDER, "accessKeyId");
        String accessKeySecret = StorageConfig.required(config, PROVIDER, "accessKeySecret");
        String publicUrl = StorageConfig.publicUrl(config, PROVIDER);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(content.length);
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            client.putObject(bucket, objectKey, new ByteArrayInputStream(content), metadata);
        } finally {
            client.shutdown();
        }
        return new StoredFile(objectKey, publicUrl + "/" + objectKey, content.length);
    }
}
