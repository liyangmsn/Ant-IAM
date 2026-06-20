package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.lark.oapi.Client;
import com.lark.oapi.service.contact.v3.model.ChildrenDepartmentReq;
import com.lark.oapi.service.contact.v3.model.ChildrenDepartmentResp;
import com.lark.oapi.service.contact.v3.model.ChildrenDepartmentRespBody;
import com.lark.oapi.service.contact.v3.model.Department;
import com.lark.oapi.service.contact.v3.model.FindByDepartmentUserReq;
import com.lark.oapi.service.contact.v3.model.FindByDepartmentUserResp;
import com.lark.oapi.service.contact.v3.model.FindByDepartmentUserRespBody;
import com.lark.oapi.service.contact.v3.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeishuIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private final ObjectMapper objectMapper;
    private final JsonIdentitySourceConnectorAdapter jsonAdapter;

    @Override
    public boolean supports(IdentitySourceType type) {
        return type == IdentitySourceType.FEISHU;
    }

    @Override
    public DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector) {
        JsonNode config = readConfig(connector.getConfiguration());
        if (config.has("payload")) {
            return jsonAdapter.readPayload(connector.getConfiguration());
        }
        return fetchDirectory(config, client(config));
    }

    private Client client(JsonNode config) {
        Client.Builder builder = Client.newBuilder(required(text(config, "appId"), "appId"), required(text(config, "appSecret"), "appSecret"));
        String endpoint = text(config, "endpoint");
        if (endpoint != null) {
            builder.openBaseUrl(endpoint);
        }
        return builder.build();
    }

    private DirectorySyncPayload fetchDirectory(JsonNode config, Client client) {
        List<DirectoryOrganization> organizations = new ArrayList<>();
        List<DirectoryUser> users = new ArrayList<>();
        Queue<DepartmentRef> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        String rootDepartmentId = textOrDefault(config, "rootDepartmentId", "0");
        String idType = textOrDefault(config, "departmentIdType", "open_department_id");
        queue.add(new DepartmentRef(rootDepartmentId, null));
        while (!queue.isEmpty()) {
            DepartmentRef current = queue.remove();
            if (!visited.add(current.id())) {
                continue;
            }
            List<DepartmentRef> children = fetchDepartments(client, current, idType, organizations);
            queue.addAll(children);
            users.addAll(fetchUsers(client, current.id(), idType));
        }
        return new DirectorySyncPayload(organizations, users, List.of());
    }

    private List<DepartmentRef> fetchDepartments(
        Client client,
        DepartmentRef parent,
        String idType,
        List<DirectoryOrganization> organizations
    ) {
        List<DepartmentRef> values = new ArrayList<>();
        String pageToken = null;
        boolean hasMore;
        do {
            ChildrenDepartmentRespBody data = fetchDepartmentPage(client, parent.id(), idType, pageToken);
            Department[] items = data.getItems() == null ? new Department[0] : data.getItems();
            for (Department department : items) {
                String id = departmentId(department, idType);
                String code = "feishu:" + id;
                organizations.add(new DirectoryOrganization(code, defaultString(department.getName(), code), parent.code()));
                values.add(new DepartmentRef(id, code));
            }
            pageToken = data.getPageToken();
            hasMore = Boolean.TRUE.equals(data.getHasMore()) && pageToken != null && !pageToken.isBlank();
        } while (hasMore);
        return values;
    }

    private ChildrenDepartmentRespBody fetchDepartmentPage(Client client, String departmentId, String idType, String pageToken) {
        try {
            ChildrenDepartmentReq request = new ChildrenDepartmentReq();
            request.setDepartmentId(departmentId);
            request.setDepartmentIdType(idType);
            request.setPageSize(50);
            request.setPageToken(pageToken);
            ChildrenDepartmentResp response = client.contact().v3().department().children(request);
            assertSuccess(response);
            return response.getData() == null ? new ChildrenDepartmentRespBody() : response.getData();
        } catch (Exception ex) {
            throw new IllegalStateException("Feishu connector department request failed: " + ex.getMessage(), ex);
        }
    }

    private List<DirectoryUser> fetchUsers(Client client, String departmentId, String idType) {
        List<DirectoryUser> values = new ArrayList<>();
        String pageToken = null;
        boolean hasMore;
        do {
            FindByDepartmentUserRespBody data = fetchUserPage(client, departmentId, idType, pageToken);
            User[] items = data.getItems() == null ? new User[0] : data.getItems();
            for (User user : items) {
                values.add(new DirectoryUser(
                userId(user),
                defaultString(user.getName(), userId(user)),
                defaultString(user.getEmail(), user.getEnterpriseEmail()),
                mobile(user),
                "feishu:" + departmentId));
            }
            pageToken = data.getPageToken();
            hasMore = Boolean.TRUE.equals(data.getHasMore()) && pageToken != null && !pageToken.isBlank();
        } while (hasMore);
        return values;
    }

    private FindByDepartmentUserRespBody fetchUserPage(Client client, String departmentId, String idType, String pageToken) {
        try {
            FindByDepartmentUserReq request = new FindByDepartmentUserReq();
            request.setDepartmentId(departmentId);
            request.setDepartmentIdType(idType);
            request.setUserIdType("user_id");
            request.setPageSize(50);
            request.setPageToken(pageToken);
            FindByDepartmentUserResp response = client.contact().v3().user().findByDepartment(request);
            assertSuccess(response);
            return response.getData() == null ? new FindByDepartmentUserRespBody() : response.getData();
        } catch (Exception ex) {
            throw new IllegalStateException("Feishu connector user request failed: " + ex.getMessage(), ex);
        }
    }

    private String departmentId(Department department, String idType) {
        if ("department_id".equals(idType)) {
            return required(department.getDepartmentId(), "department.department_id");
        }
        return defaultString(department.getOpenDepartmentId(), required(department.getDepartmentId(), "department.department_id"));
    }

    private String userId(User user) {
        String userId = user.getUserId();
        if (userId != null) {
            return userId;
        }
        String openId = user.getOpenId();
        return openId == null ? required(user.getUnionId(), "user.union_id") : openId;
    }

    private String mobile(User user) {
        return user.getMobile();
    }

    private JsonNode readConfig(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("Feishu connector configuration is required");
        }
        try {
            return objectMapper.readTree(configuration);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Feishu connector configuration must be valid JSON", ex);
        }
    }

    private void assertSuccess(ChildrenDepartmentResp response) {
        if (response == null || !response.success()) {
            String message = response == null ? "empty response" : response.getMsg();
            throw new IllegalStateException("Feishu connector request failed: " + message);
        }
    }

    private void assertSuccess(FindByDepartmentUserResp response) {
        if (response == null || !response.success()) {
            String message = response == null ? "empty response" : response.getMsg();
            throw new IllegalStateException("Feishu connector request failed: " + message);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Feishu connector field is required: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        String value = text(node, field);
        return value == null ? defaultValue : value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record DepartmentRef(String id, String code) {
    }
}
