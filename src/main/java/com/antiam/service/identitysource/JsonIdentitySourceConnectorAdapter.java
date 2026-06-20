package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(IdentitySourceType type) {
        return type == IdentitySourceType.LOCAL
            || type == IdentitySourceType.LDAP
            || type == IdentitySourceType.ACTIVE_DIRECTORY
            || type == IdentitySourceType.WECHAT_WORK
            || type == IdentitySourceType.SCIM;
    }

    @Override
    public DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector) {
        return readPayload(connector.getConfiguration());
    }

    DirectorySyncPayload readPayload(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return DirectorySyncPayload.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(configuration);
            JsonNode payload = root.has("payload") ? root.path("payload") : root;
            return objectMapper.treeToValue(payload, DirectorySyncPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Connector configuration must be valid JSON sync payload", ex);
        }
    }
}
