package com.antiam.service.identitysource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectorySyncPayloadReader {

    private final ObjectMapper objectMapper;

    /**
     * 解析目录同步载荷，既接受直接的组织/用户/用户组结构，也接受包裹在 payload 字段中的结构。
     */
    public DirectorySyncPayload read(String body) {
        if (body == null || body.isBlank()) {
            return DirectorySyncPayload.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode payload = root.has("payload") ? root.path("payload") : root;
            return objectMapper.treeToValue(payload, DirectorySyncPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Directory sync payload must be valid JSON", ex);
        }
    }
}
