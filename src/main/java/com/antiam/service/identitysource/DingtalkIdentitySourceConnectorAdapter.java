package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiGettokenRequest;
import com.dingtalk.api.request.OapiUserListsimpleRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserGetRequest;
import com.dingtalk.api.response.OapiGettokenResponse;
import com.dingtalk.api.response.OapiUserListsimpleResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taobao.api.ApiException;
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
public class DingtalkIdentitySourceConnectorAdapter implements IdentitySourceConnectorAdapter {

    private static final String DEFAULT_ENDPOINT = "https://oapi.dingtalk.com";

    private final ObjectMapper objectMapper;
    private final JsonIdentitySourceConnectorAdapter jsonAdapter;

    @Override
    public boolean supports(IdentitySourceType type) {
        return type == IdentitySourceType.DINGTALK;
    }

    @Override
    public DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector) {
        JsonNode config = readConfig(connector.getConfiguration());
        if (config.has("payload")) {
            return jsonAdapter.readPayload(connector.getConfiguration());
        }
        String accessToken = text(config, "accessToken");
        if (accessToken == null) {
            accessToken = fetchAccessToken(config);
        }
        return fetchDirectory(config, accessToken);
    }

    private String fetchAccessToken(JsonNode config) {
        try {
            OapiGettokenRequest request = new OapiGettokenRequest();
            request.setAppkey(required(config, "appKey"));
            request.setAppsecret(required(config, "appSecret"));
            request.setHttpMethod("GET");
            OapiGettokenResponse response = new DefaultDingTalkClient(endpoint(config) + "/gettoken").execute(request);
            assertSuccess(response);
            return response.getAccessToken();
        } catch (ApiException ex) {
            throw new IllegalStateException("DingTalk connector token request failed: " + ex.getMessage(), ex);
        }
    }

    private DirectorySyncPayload fetchDirectory(JsonNode config, String accessToken) {
        List<DirectoryOrganization> organizations = new ArrayList<>();
        List<DirectoryUser> users = new ArrayList<>();
        Queue<DepartmentRef> queue = new ArrayDeque<>();
        Set<Long> visited = new LinkedHashSet<>();
        long rootDeptId = config.path("rootDeptId").asLong(1L);
        queue.add(new DepartmentRef(rootDeptId, null));
        while (!queue.isEmpty()) {
            DepartmentRef current = queue.remove();
            if (!visited.add(current.id())) {
                continue;
            }
            for (OapiV2DepartmentListsubResponse.DeptBaseResponse department : fetchDepartments(config, accessToken, current.id())) {
                long id = department.getDeptId();
                String code = "dingtalk:" + id;
                organizations.add(new DirectoryOrganization(code, defaultString(department.getName(), code), current.parentCode()));
                queue.add(new DepartmentRef(id, code));
            }
            users.addAll(fetchUsers(config, accessToken, current.id(), shouldFetchUserDetail(config)));
        }
        return new DirectorySyncPayload(organizations, users, List.of());
    }

    private List<OapiV2DepartmentListsubResponse.DeptBaseResponse> fetchDepartments(JsonNode config, String accessToken, long deptId) {
        try {
            OapiV2DepartmentListsubRequest request = new OapiV2DepartmentListsubRequest();
            request.setDeptId(deptId);
            request.setLanguage(language(config));
            OapiV2DepartmentListsubResponse response = new DefaultDingTalkClient(endpoint(config) + "/topapi/v2/department/listsub")
                .execute(request, accessToken);
            assertSuccess(response);
            return response.getResult() == null ? List.of() : response.getResult();
        } catch (ApiException ex) {
            throw new IllegalStateException("DingTalk connector department request failed: " + ex.getMessage(), ex);
        }
    }

    private List<DirectoryUser> fetchUsers(JsonNode config, String accessToken, long deptId, boolean fetchUserDetail) {
        List<DirectoryUser> values = new ArrayList<>();
        long cursor = 0L;
        boolean hasMore;
        do {
            OapiUserListsimpleResponse.PageResult result = fetchUserPage(config, accessToken, deptId, cursor);
            List<OapiUserListsimpleResponse.ListUserSimpleResponse> users = result.getList() == null ? List.of() : result.getList();
            for (OapiUserListsimpleResponse.ListUserSimpleResponse user : users) {
                UserProfile userProfile = fetchUserDetail
                    ? fetchUserDetail(config, accessToken, required(user.getUserid(), "user.userid"))
                    : new UserProfile(required(user.getUserid(), "user.userid"), user.getName(), null, null);
                values.add(new DirectoryUser(
                    userProfile.userId(),
                    defaultString(userProfile.name(), userProfile.userId()),
                    userProfile.email(),
                    userProfile.mobile(),
                    "dingtalk:" + deptId));
            }
            hasMore = Boolean.TRUE.equals(result.getHasMore());
            cursor = result.getNextCursor() == null ? cursor + 100 : result.getNextCursor();
        } while (hasMore);
        return values;
    }

    private OapiUserListsimpleResponse.PageResult fetchUserPage(JsonNode config, String accessToken, long deptId, long cursor) {
        try {
            OapiUserListsimpleRequest request = new OapiUserListsimpleRequest();
            request.setDeptId(deptId);
            request.setCursor(cursor);
            request.setSize(100L);
            request.setLanguage(language(config));
            OapiUserListsimpleResponse response = new DefaultDingTalkClient(endpoint(config) + "/topapi/user/listsimple")
                .execute(request, accessToken);
            assertSuccess(response);
            return response.getResult() == null ? new OapiUserListsimpleResponse.PageResult() : response.getResult();
        } catch (ApiException ex) {
            throw new IllegalStateException("DingTalk connector user list request failed: " + ex.getMessage(), ex);
        }
    }

    private UserProfile fetchUserDetail(JsonNode config, String accessToken, String userId) {
        try {
            OapiV2UserGetRequest request = new OapiV2UserGetRequest();
            request.setUserid(userId);
            request.setLanguage(language(config));
            OapiV2UserGetResponse response = new DefaultDingTalkClient(endpoint(config) + "/topapi/v2/user/get")
                .execute(request, accessToken);
            assertSuccess(response);
            OapiV2UserGetResponse.UserGetResponse result = response.getResult();
            if (result == null) {
                return new UserProfile(userId, userId, null, null);
            }
            return new UserProfile(
                defaultString(result.getUserid(), userId),
                result.getName(),
                defaultString(result.getEmail(), result.getOrgEmail()),
                result.getMobile());
        } catch (ApiException ex) {
            throw new IllegalStateException("DingTalk connector user detail request failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode readConfig(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException("DingTalk connector configuration is required");
        }
        try {
            return objectMapper.readTree(configuration);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("DingTalk connector configuration must be valid JSON", ex);
        }
    }

    private String endpoint(JsonNode config) {
        return textOrDefault(config, "endpoint", DEFAULT_ENDPOINT);
    }

    private boolean shouldFetchUserDetail(JsonNode config) {
        return !config.has("fetchUserDetail") || config.path("fetchUserDetail").asBoolean(true);
    }

    private String language(JsonNode config) {
        return textOrDefault(config, "language", "zh_CN");
    }

    private void assertSuccess(OapiGettokenResponse response) {
        if (response == null || !response.isSuccess() || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
            String message = response == null ? "empty response" : defaultString(response.getErrmsg(), "access token is missing");
            throw new IllegalStateException("DingTalk connector request failed: " + message);
        }
    }

    private void assertSuccess(OapiV2DepartmentListsubResponse response) {
        if (response == null || !response.isSuccess()) {
            String message = response == null ? "empty response" : defaultString(response.getErrmsg(), "unknown error");
            throw new IllegalStateException("DingTalk connector request failed: " + message);
        }
    }

    private void assertSuccess(OapiUserListsimpleResponse response) {
        if (response == null || !response.isSuccess()) {
            String message = response == null ? "empty response" : defaultString(response.getErrmsg(), "unknown error");
            throw new IllegalStateException("DingTalk connector request failed: " + message);
        }
    }

    private void assertSuccess(OapiV2UserGetResponse response) {
        if (response == null || !response.isSuccess()) {
            String message = response == null ? "empty response" : defaultString(response.getErrmsg(), "unknown error");
            throw new IllegalStateException("DingTalk connector request failed: " + message);
        }
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("DingTalk connector field is required: " + field);
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

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DingTalk connector field is required: " + field);
        }
        return value;
    }

    private record DepartmentRef(long id, String parentCode) {
    }

    private record UserProfile(String userId, String name, String email, String mobile) {
    }
}
