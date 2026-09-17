package com.antiam.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;

@Component
public class TencentObjectStorageAdapter implements ObjectStorageAdapter {

    private static final String PROVIDER = "tencent";

    @Override
    public boolean supports(String provider) {
        return PROVIDER.equals(provider);
    }

    @Override
    public StoredFile store(JsonNode config, String objectKey, String contentType, byte[] content) {
        String bucket = bucketName(config);
        String region = StorageConfig.required(config, PROVIDER, "region");
        String secretId = StorageConfig.required(config, PROVIDER, "secretId");
        String secretKey = StorageConfig.required(config, PROVIDER, "secretKey");
        String publicUrl = StorageConfig.publicUrl(config, PROVIDER);
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        COSClient client = new COSClient(credentials, new ClientConfig(new Region(region)));
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(content.length);
            client.putObject(new PutObjectRequest(bucket, objectKey, new ByteArrayInputStream(content), metadata));
        } finally {
            client.shutdown();
        }
        return new StoredFile(objectKey, publicUrl + "/" + objectKey, content.length);
    }

    /**
     * 腾讯云 COS 的 bucket 必须带 AppId 后缀，控制台按 Bucket 与 AppId 两个字段采集。
     */
    private String bucketName(JsonNode config) {
        String bucket = StorageConfig.required(config, PROVIDER, "bucket");
        String appId = StorageConfig.required(config, PROVIDER, "appId");
        return bucket + "-" + appId;
    }
}
