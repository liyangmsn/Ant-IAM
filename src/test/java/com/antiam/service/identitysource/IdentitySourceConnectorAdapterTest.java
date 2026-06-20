package com.antiam.service.identitysource;

import static org.assertj.core.api.Assertions.assertThat;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IdentitySourceConnectorAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JsonIdentitySourceConnectorAdapter jsonAdapter = new JsonIdentitySourceConnectorAdapter(objectMapper);

    @Test
    void readsLegacyJsonPayload() {
        DirectorySyncPayload payload = jsonAdapter.readPayload("""
            {
              "organizations": [{"code": "engineering", "name": "Engineering"}],
              "users": [{"username": "alice", "displayName": "Alice", "organizationCode": "engineering"}],
              "groups": [{"code": "admins", "name": "Administrators", "members": ["alice"]}]
            }
            """);

        assertThat(payload.organizations()).extracting(DirectoryOrganization::code).containsExactly("engineering");
        assertThat(payload.users()).extracting(DirectoryUser::username).containsExactly("alice");
        assertThat(payload.groups()).extracting(DirectoryGroup::code).containsExactly("admins");
    }

    @Test
    void dingtalkCanUseEmbeddedPayloadForLocalDryRuns() {
        DingtalkIdentitySourceConnectorAdapter adapter = new DingtalkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        DirectorySyncPayload payload = adapter.load(
            source(IdentitySourceType.DINGTALK),
            connector(IdentitySourceType.DINGTALK, """
                {
                  "payload": {
                    "users": [{"username": "dt-alice", "displayName": "Alice"}]
                  }
                }
                """));

        assertThat(payload.users()).extracting(DirectoryUser::username).containsExactly("dt-alice");
    }

    @Test
    void feishuCanUseEmbeddedPayloadForLocalDryRuns() {
        FeishuIdentitySourceConnectorAdapter adapter = new FeishuIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        DirectorySyncPayload payload = adapter.load(
            source(IdentitySourceType.FEISHU),
            connector(IdentitySourceType.FEISHU, """
                {
                  "payload": {
                    "organizations": [{"code": "fs-product", "name": "Product"}]
                  }
                }
                """));

        assertThat(payload.organizations()).extracting(DirectoryOrganization::code).containsExactly("fs-product");
    }

    private IdentitySource source(IdentitySourceType type) {
        return new IdentitySource(type.name().toLowerCase(), type.name(), null, type, null);
    }

    private IdentitySourceConnector connector(IdentitySourceType type, String configuration) {
        return new IdentitySourceConnector(source(type), configuration, null);
    }
}
