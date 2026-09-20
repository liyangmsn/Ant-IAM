package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private final DirectorySyncPayloadReader payloadReader;

    public JsonIdentitySourceConnectorAdapter(ObjectMapper objectMapper) {
        this.payloadReader = new DirectorySyncPayloadReader(objectMapper);
    }

    @Override
    public boolean supports(IdentitySourceType type) {
        return type == IdentitySourceType.LOCAL
            || type == IdentitySourceType.LDAP
            || type == IdentitySourceType.ACTIVE_DIRECTORY
            || type == IdentitySourceType.SCIM;
    }

    @Override
    public DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector) {
        return readPayload(connector.getConfiguration());
    }

    DirectorySyncPayload readPayload(String configuration) {
        return payloadReader.read(configuration);
    }
}
