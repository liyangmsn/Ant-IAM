package com.antiam.service.storage;

import com.fasterxml.jackson.databind.JsonNode;

public interface ObjectStorageAdapter {

    boolean supports(String provider);

    StoredFile store(JsonNode config, String objectKey, String contentType, byte[] content);
}
