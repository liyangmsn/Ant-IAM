package com.antiam.service.identitysource;

import static org.assertj.core.api.Assertions.assertThat;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.service.wechatwork.WechatWorkClient;
import com.dingtalk.api.response.OapiRoleListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.contact.v3.model.Group;
import java.util.List;
import java.util.Set;
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

    @Test
    void wechatWorkOrdersDepartmentsParentFirstUnderTheRoot() throws Exception {
        WechatWorkIdentitySourceConnectorAdapter adapter = new WechatWorkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter, new WechatWorkClient(objectMapper));

        List<DirectoryOrganization> organizations = adapter.toOrganizations(
            source(IdentitySourceType.WECHAT_WORK),
            objectMapper.readTree("{}"),
            1L,
            List.of(
                new WechatWorkIdentitySourceConnectorAdapter.WechatWorkDepartment(3L, 2L, "Backend"),
                new WechatWorkIdentitySourceConnectorAdapter.WechatWorkDepartment(1L, 0L, "Ant Corp"),
                new WechatWorkIdentitySourceConnectorAdapter.WechatWorkDepartment(2L, 1L, "R&D")));

        assertThat(organizations).containsExactly(
            new DirectoryOrganization("wechat-work:1", "Ant Corp", null),
            new DirectoryOrganization("wechat-work:2", "R&D", "wechat-work:1"),
            new DirectoryOrganization("wechat-work:3", "Backend", "wechat-work:2"));
    }

    @Test
    void wechatWorkUserUsesMainDepartmentAndBizMailFallback() throws Exception {
        WechatWorkIdentitySourceConnectorAdapter adapter = new WechatWorkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter, new WechatWorkClient(objectMapper));

        DirectoryUser user = adapter.toDirectoryUser(objectMapper.readTree("""
            {"userid":"zhangsan","name":"张三","department":[2,3],"main_department":3,"biz_mail":"zs@corp.com","mobile":"13800000000"}
            """), Set.of(1L, 2L, 3L), 1L);

        assertThat(user).isEqualTo(new DirectoryUser("zhangsan", "张三", "zs@corp.com", "13800000000", "wechat-work:3"));
    }

    @Test
    void wechatWorkMapsTagsToUserGroups() {
        WechatWorkIdentitySourceConnectorAdapter adapter = new WechatWorkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter, new WechatWorkClient(objectMapper));

        assertThat(adapter.toDirectoryGroup(7L, "Admins", List.of("zhangsan")))
            .isEqualTo(new DirectoryGroup("wechat-work:tag:7", "Admins", List.of("zhangsan")));
    }

    @Test
    void wechatWorkMapsTagDepartmentsToSyncedUsers() throws Exception {
        WechatWorkIdentitySourceConnectorAdapter adapter = new WechatWorkIdentitySourceConnectorAdapter(objectMapper, jsonAdapter, new WechatWorkClient(objectMapper));

        assertThat(adapter.tagMembers(
            objectMapper.readTree("""
                {"userlist":[{"userid":"zhangsan"}],"partylist":[2]}
                """),
            List.of(
                new DirectoryUser("zhangsan", "张三", null, null, "wechat-work:1"),
                new DirectoryUser("lisi", "李四", null, null, "wechat-work:2"))))
            .containsExactly("zhangsan", "lisi");
    }

    private IdentitySource source(IdentitySourceType type) {
        return new IdentitySource(type.name().toLowerCase(), type.name(), null, type, null);
    }

    private IdentitySourceConnector connector(IdentitySourceType type, String configuration) {
        return new IdentitySourceConnector(source(type), configuration, null);
    }
}
