package com.antiam.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.stereotype.Component;

@Component
public class QiniuObjectStorageAdapter implements ObjectStorageAdapter {

    private static final String PROVIDER = "qiniu";

    @Override
    public boolean supports(String provider) {
        return PROVIDER.equals(provider);
    }

    @Override
    public StoredFile store(JsonNode config, String objectKey, String contentType, byte[] content) {
        String bucket = StorageConfig.required(config, PROVIDER, "bucket");
        String accessKey = StorageConfig.required(config, PROVIDER, "accessKey");
        String secretKey = StorageConfig.required(config, PROVIDER, "secretKey");
        String publicUrl = StorageConfig.publicUrl(config, PROVIDER);
        Auth auth = Auth.create(accessKey, secretKey);
        UploadManager uploadManager = new UploadManager(new Configuration(Region.autoRegion()));
        try {
            Response response = uploadManager.put(content, objectKey, auth.uploadToken(bucket));
            if (!response.isOK()) {
                throw new IllegalStateException("Qiniu upload failed: " + response.bodyString());
            }
        } catch (QiniuException ex) {
            throw new IllegalStateException("Qiniu upload failed: " + ex.getMessage(), ex);
        }
        return new StoredFile(objectKey, publicUrl + "/" + objectKey, content.length);
    }
}
