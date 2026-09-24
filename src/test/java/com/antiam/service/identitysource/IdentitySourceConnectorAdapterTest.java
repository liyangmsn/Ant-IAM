package com.antiam.service.identitysource;

import static org.assertj.core.api.Assertions.assertThat;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.dingtalk.api.response.OapiRoleListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.contact.v3.model.Group;
import java.util.List;
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
    void dingtalkIncludesTheConfiguredRootDepartment() throws Exception {
        DingtalkIdentitySourceConnectorAdapter adapter = new DingtalkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        IdentitySource source = source(IdentitySourceType.DINGTALK);

        DirectoryOrganization root = adapter.rootOrganization(source, objectMapper.readTree("{\"rootDeptName\":\"Develop Team\"}"), 1L);

        assertThat(root).isEqualTo(new DirectoryOrganization("dingtalk:1", "Develop Team", null));
        assertThat(adapter.rootDepartmentCode(1L)).isEqualTo(root.code());
    }

    @Test
    void dingtalkMapsRoleGroupsToUserGroups() {
        DingtalkIdentitySourceConnectorAdapter adapter = new DingtalkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        OapiRoleListResponse.OpenRoleGroup group = new OapiRoleListResponse.OpenRoleGroup();
        group.setGroupId(42L);
        group.setName("Engineering");

        assertThat(adapter.toDirectoryGroup(group, List.of("manager1830")))
            .isEqualTo(new DirectoryGroup("dingtalk:role-group:42", "Engineering", List.of("manager1830")));
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

    @Test
    void feishuIncludesTheConfiguredRootDepartment() throws Exception {
        FeishuIdentitySourceConnectorAdapter adapter = new FeishuIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        IdentitySource source = source(IdentitySourceType.FEISHU);

        DirectoryOrganization root = adapter.rootOrganization(source, objectMapper.readTree("{\"rootDepartmentName\":\"Feishu Team\"}"), "0");

        assertThat(root).isEqualTo(new DirectoryOrganization("feishu:0", "Feishu Team", null));
        assertThat(adapter.rootDepartmentCode("0")).isEqualTo(root.code());
    }

    @Test
    void feishuMapsUserGroupMembers() {
        FeishuIdentitySourceConnectorAdapter adapter = new FeishuIdentitySourceConnectorAdapter(objectMapper, jsonAdapter);
        Group group = new Group();
        group.setId("ug-1");
        group.setName("Engineering");

        assertThat(adapter.toDirectoryGroup(group, List.of("user-1")))
            .isEqualTo(new DirectoryGroup("feishu:group:ug-1", "Engineering", List.of("user-1")));
    }

    private IdentitySource source(IdentitySourceType type) {
        return new IdentitySource(type.name().toLowerCase(), type.name(), null, type, null);
    }

    private IdentitySourceConnector connector(IdentitySourceType type, String configuration) {
        return new IdentitySourceConnector(source(type), configuration, null);
    }
}
