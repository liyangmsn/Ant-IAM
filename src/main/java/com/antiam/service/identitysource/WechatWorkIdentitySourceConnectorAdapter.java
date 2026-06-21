package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class WechatWorkIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private static final String DEFAULT_ENDPOINT = "https://qyapi.weixin.qq.com";

    private final ObjectMapper objectMapper;
    private final JsonIdentitySourceConnectorAdapter jsonAdapter;

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
        String accessToken = fetchAccessToken(config);
        return fetchDirectory(config, accessToken);
    }

    private String fetchAccessToken(JsonNode config) {
        JsonNode response = get(config, "/cgi-bin/gettoken?corpid={corpId}&corpsecret={corpSecret}",
            requiredAny(config, "corpId", "appId"),
            requiredAny(config, "corpSecret", "appSecret"));
        assertSuccess(response);
        return required(response, "access_token");
    }

    private DirectorySyncPayload fetchDirectory(JsonNode config, String accessToken) {
        long rootDeptId = config.path("rootDeptId").asLong(1L);
        JsonNode departmentsResponse = get(config, "/cgi-bin/department/list?access_token={accessToken}&id={departmentId}", accessToken, rootDeptId);
        assertSuccess(departmentsResponse);

        List<DirectoryOrganization> organizations = new ArrayList<>();
        for (JsonNode department : departmentsResponse.path("department")) {
            long id = department.path("id").asLong();
            if (id == rootDeptId) {
                continue;
            }
            String code = departmentCode(id);
            long parentId = department.path("parentid").asLong(rootDeptId);
            organizations.add(new DirectoryOrganization(
                code,
                defaultString(text(department, "name"), code),
                parentId == rootDeptId ? null : departmentCode(parentId)));
        }

        List<DirectoryUser> users = new ArrayList<>();
        List<Long> departmentIds = new ArrayList<>();
        departmentIds.add(rootDeptId);
        departmentsResponse.path("department").forEach(department -> departmentIds.add(department.path("id").asLong()));
        for (Long departmentId : departmentIds.stream().distinct().toList()) {
            users.addAll(fetchUsers(config, accessToken, departmentId));
        }
        return new DirectorySyncPayload(organizations, users, List.of());
    }

    private List<DirectoryUser> fetchUsers(JsonNode config, String accessToken, long departmentId) {
        JsonNode response = get(config, "/cgi-bin/user/simplelist?access_token={accessToken}&department_id={departmentId}&fetch_child=0",
            accessToken,
            departmentId);
        assertSuccess(response);
        List<DirectoryUser> users = new ArrayList<>();
        for (JsonNode user : response.path("userlist")) {
            String userId = required(user, "userid");
            users.add(new DirectoryUser(
                userId,
                defaultString(text(user, "name"), userId),
                text(user, "email"),
                text(user, "mobile"),
                departmentCode(departmentId)));
        }
        return users;
    }

    private JsonNode get(JsonNode config, String uri, Object... variables) {
        return RestClient.create(endpoint(config))
            .get()
            .uri(uri, variables)
            .retrieve()
            .body(JsonNode.class);
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

    private void assertSuccess(JsonNode response) {
        if (response == null || response.path("errcode").asInt(-1) != 0) {
            String message = response == null ? "empty response" : defaultString(text(response, "errmsg"), "unknown error");
            throw new IllegalStateException("WeCom connector request failed: " + message);
        }
    }

    private String endpoint(JsonNode config) {
        return defaultString(text(config, "endpoint"), DEFAULT_ENDPOINT);
    }

    private String departmentCode(long id) {
        return "wechat-work:" + id;
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
}
