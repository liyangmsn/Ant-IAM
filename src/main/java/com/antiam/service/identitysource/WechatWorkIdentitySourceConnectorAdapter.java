package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.antiam.service.wechatwork.WechatWorkClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatWorkIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private final ObjectMapper objectMapper;
    private final JsonIdentitySourceConnectorAdapter jsonAdapter;
    private final WechatWorkClient client;

    @Override
    public boolean supports(IdentitySourceType type) {
        return type == IdentitySourceType.WECHAT_WORK;
    }

    @Override
    public DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector) {
        JsonNode config = readConfig(connector.getConfiguration());
        if (config.has("payload")) {
            return jsonAdapter.readPayload(connector.getConfiguration());
        }
        Api api = new Api(config);
        long rootDeptId = config.path("rootDeptId").asLong(1L);
        List<WechatWorkDepartment> departments = fetchDepartments(api, rootDeptId);
        List<DirectoryOrganization> organizations = toOrganizations(source, config, rootDeptId, departments);
        Set<Long> departmentIds = new LinkedHashSet<>();
        departments.forEach(department -> departmentIds.add(department.id()));
        departmentIds.add(rootDeptId);
        List<DirectoryUser> users = fetchUsers(api, config, rootDeptId, departmentIds);
        List<DirectoryGroup> groups = config.path("syncTags").asBoolean(true) ? fetchTags(api, users) : List.of();
        return new DirectorySyncPayload(organizations, users, groups);
    }

    /**
     * 按父部门在前的顺序生成组织，保证同步时父组织已先落库；根部门之外的孤儿部门挂到根部门下。
     */
    List<DirectoryOrganization> toOrganizations(
        IdentitySource source,
        JsonNode config,
        long rootDeptId,
        List<WechatWorkDepartment> departments
    ) {
        Map<Long, WechatWorkDepartment> byId = new LinkedHashMap<>();
        departments.forEach(department -> byId.put(department.id(), department));
        Map<Long, List<WechatWorkDepartment>> children = new HashMap<>();
        for (WechatWorkDepartment department : departments) {
            if (department.id() == rootDeptId) {
                continue;
            }
            long parentId = byId.containsKey(department.parentId()) ? department.parentId() : rootDeptId;
            children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(department);
        }

        WechatWorkDepartment root = byId.get(rootDeptId);
        List<DirectoryOrganization> organizations = new ArrayList<>();
        organizations.add(new DirectoryOrganization(
            departmentCode(rootDeptId),
            defaultString(text(config, "rootDeptName"), defaultString(root == null ? null : root.name(), defaultString(source.getName(), "WeCom"))),
            null));
        Queue<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new LinkedHashSet<>();
        queue.add(rootDeptId);
        while (!queue.isEmpty()) {
            long parentId = queue.remove();
            if (!visited.add(parentId)) {
                continue;
            }
            for (WechatWorkDepartment child : children.getOrDefault(parentId, List.of())) {
                String code = departmentCode(child.id());
                organizations.add(new DirectoryOrganization(code, defaultString(child.name(), code), departmentCode(parentId)));
                queue.add(child.id());
            }
        }
        return organizations;
    }

    /**
     * 将成员映射为目录用户：多部门成员只导入一次，优先归属主部门。
     */
    DirectoryUser toDirectoryUser(JsonNode user, Set<Long> departmentIds, long rootDeptId) {
        String userId = required(user, "userid");
        return new DirectoryUser(
            userId,
            defaultString(text(user, "name"), userId),
            defaultString(text(user, "email"), text(user, "biz_mail")),
            text(user, "mobile"),
            departmentCode(primaryDepartment(user, departmentIds, rootDeptId)));
    }

    DirectoryGroup toDirectoryGroup(long tagId, String tagName, List<String> members) {
        String code = tagCode(tagId);
        return new DirectoryGroup(code, defaultString(tagName, code), members);
    }

    String departmentCode(long id) {
        return "wechat-work:" + id;
    }

    String tagCode(long id) {
        return "wechat-work:tag:" + id;
    }

    private List<WechatWorkDepartment> fetchDepartments(Api api, long rootDeptId) {
        JsonNode response = api.get("/cgi-bin/department/list", Map.of("id", rootDeptId));
        if (WechatWorkClient.isSuccess(response)) {
            List<WechatWorkDepartment> values = new ArrayList<>();
            response.path("department").forEach(department -> values.add(new WechatWorkDepartment(
                department.path("id").asLong(),
                department.path("parentid").asLong(rootDeptId),
                text(department, "name"))));
            return values;
        }
        // 新创建的应用或通讯录同步新 IP 可能无法调用 department/list，回退到 ID 列表 + 单部门详情。
        log.info("WeCom department/list unavailable ({}), falling back to department/simplelist", WechatWorkClient.errorMessage(response));
        JsonNode simpleList = api.get("/cgi-bin/department/simplelist", Map.of("id", rootDeptId));
        assertSuccess(simpleList, "department/simplelist");
        List<WechatWorkDepartment> values = new ArrayList<>();
        for (JsonNode department : simpleList.path("department_id")) {
            long id = department.path("id").asLong();
            JsonNode detail = api.get("/cgi-bin/department/get", Map.of("id", id));
            String name = WechatWorkClient.isSuccess(detail) ? text(detail.path("department"), "name") : null;
            values.add(new WechatWorkDepartment(id, department.path("parentid").asLong(rootDeptId), name));
        }
        return values;
    }

    private List<DirectoryUser> fetchUsers(Api api, JsonNode config, long rootDeptId, Set<Long> departmentIds) {
        Map<String, DirectoryUser> users = new LinkedHashMap<>();
        for (long departmentId : departmentIds) {
            JsonNode response = api.get("/cgi-bin/user/list", Map.of("department_id", departmentId));
            if (!WechatWorkClient.isSuccess(response)) {
                // 通讯录同步新 IP 不能再调用 user/list，改用成员 ID 列表逐个读取详情。
                log.info("WeCom user/list unavailable ({}), falling back to user/list_id", WechatWorkClient.errorMessage(response));
                return fetchUsersById(api, config, rootDeptId, departmentIds);
            }
            for (JsonNode user : response.path("userlist")) {
                DirectoryUser value = toDirectoryUser(user, departmentIds, rootDeptId);
                users.putIfAbsent(value.username(), value);
            }
        }
        return List.copyOf(users.values());
    }

    private List<DirectoryUser> fetchUsersById(Api api, JsonNode config, long rootDeptId, Set<Long> departmentIds) {
        Map<String, DirectoryUser> users = new LinkedHashMap<>();
        String cursor = null;
        do {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("limit", 10000);
            if (cursor != null) {
                body.put("cursor", cursor);
            }
            JsonNode response = api.post("/cgi-bin/user/list_id", body);
            assertSuccess(response, "user/list_id");
            for (JsonNode item : response.path("dept_user")) {
                String userId = text(item, "userid");
                if (userId == null || users.containsKey(userId) || !departmentIds.contains(item.path("department").asLong())) {
                    continue;
                }
                JsonNode detail = api.get("/cgi-bin/user/get", Map.of("userid", userId));
                JsonNode user = WechatWorkClient.isSuccess(detail) ? detail : item;
                users.put(userId, toDirectoryUser(user, departmentIds, rootDeptId));
            }
            cursor = text(response, "next_cursor");
        } while (cursor != null);
        return List.copyOf(users.values());
    }

    private List<DirectoryGroup> fetchTags(Api api, List<DirectoryUser> users) {
        JsonNode response = api.get("/cgi-bin/tag/list", Map.of());
        if (!WechatWorkClient.isSuccess(response)) {
            log.warn("WeCom tag/list failed, skipping tag sync: {}", WechatWorkClient.errorMessage(response));
            return List.of();
        }
        List<DirectoryGroup> groups = new ArrayList<>();
        for (JsonNode tag : response.path("taglist")) {
            long tagId = tag.path("tagid").asLong();
            JsonNode detail = api.get("/cgi-bin/tag/get", Map.of("tagid", tagId));
            if (!WechatWorkClient.isSuccess(detail)) {
                log.warn("WeCom tag/get failed for tag {}, skipping: {}", tagId, WechatWorkClient.errorMessage(detail));
                continue;
            }
            groups.add(toDirectoryGroup(
                tagId,
                defaultString(text(tag, "tagname"), text(detail, "tagname")),
                tagMembers(detail, users)));
        }
        return groups;
    }

    List<String> tagMembers(JsonNode detail, List<DirectoryUser> users) {
        Set<String> knownUsers = new LinkedHashSet<>();
        Map<String, List<String>> usersByDepartment = new LinkedHashMap<>();
        for (DirectoryUser user : users) {
            knownUsers.add(user.username());
            usersByDepartment.computeIfAbsent(user.organizationCode(), ignored -> new ArrayList<>()).add(user.username());
        }
        return tagMembers(detail, usersByDepartment, knownUsers);
    }

    private List<String> tagMembers(JsonNode detail, Map<String, List<String>> usersByDepartment, Set<String> knownUsers) {
        Set<String> members = new LinkedHashSet<>();
        for (JsonNode member : detail.path("userlist")) {
            String userId = text(member, "userid");
            if (userId != null && knownUsers.contains(userId)) {
                members.add(userId);
            }
        }
        for (JsonNode department : detail.path("partylist")) {
            usersByDepartment.getOrDefault(departmentCode(department.asLong()), List.of()).forEach(members::add);
        }
        return List.copyOf(members);
    }

    private long primaryDepartment(JsonNode user, Set<Long> departmentIds, long rootDeptId) {
        long mainDepartment = user.path("main_department").asLong(0L);
        if (departmentIds.contains(mainDepartment)) {
            return mainDepartment;
        }
        JsonNode department = user.path("department");
        if (department.isArray()) {
            for (JsonNode id : department) {
                if (departmentIds.contains(id.asLong())) {
                    return id.asLong();
                }
            }
        } else if (departmentIds.contains(department.asLong(0L))) {
            return department.asLong();
        }
        return rootDeptId;
    }

    private JsonNode readConfig(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("WeCom connector configuration is required");
        }
        try {
            return objectMapper.readTree(configuration);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("WeCom connector configuration must be valid JSON", ex);
        }
    }

    private void assertSuccess(JsonNode response, String operation) {
        if (!WechatWorkClient.isSuccess(response)) {
            throw new IllegalStateException("WeCom connector " + operation + " request failed: " + WechatWorkClient.errorMessage(response));
        }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("WeCom connector field is required: " + field);
        }
        return value;
    }

    private String requiredAny(JsonNode node, String first, String second) {
        String value = text(node, first);
        if (value != null) {
            return value;
        }
        value = text(node, second);
        if (value != null) {
            return value;
        }
        throw new IllegalArgumentException("WeCom connector field is required: " + first + " or " + second);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    record WechatWorkDepartment(long id, long parentId, String name) {
    }

    private final class Api {

        private final String endpoint;
        private final String corpId;
        private final String secret;

        private Api(JsonNode config) {
            this.endpoint = defaultString(text(config, "endpoint"), WechatWorkClient.DEFAULT_ENDPOINT);
            this.corpId = requiredAny(config, "corpId", "appId");
            this.secret = requiredAny(config, "corpSecret", "appSecret");
        }

        private JsonNode get(String path, Map<String, ?> query) {
            return client.get(endpoint, corpId, secret, path, query);
        }

        private JsonNode post(String path, Object body) {
            return client.post(endpoint, corpId, secret, path, body);
        }
    }
}
