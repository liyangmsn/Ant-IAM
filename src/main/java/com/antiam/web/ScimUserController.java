package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimUserRequest;
import static com.antiam.dto.ScimDtos.ScimEmail;
import static com.antiam.dto.ScimDtos.ScimListResponse;
import static com.antiam.dto.ScimDtos.ScimName;
import static com.antiam.dto.ScimDtos.ScimPatchOperation;
import static com.antiam.dto.ScimDtos.ScimPatchRequest;
import static com.antiam.dto.ScimDtos.ScimPhoneNumber;
import static com.antiam.dto.ScimDtos.ScimUserResponse;

import com.antiam.dto.UserDtos.UserResponse;
import com.antiam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scim/v2/Users")
@RequiredArgsConstructor
@Tag(name = "SCIM 用户", description = "SCIM 2.0 用户资源同步接口")
public class ScimUserController {

    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final List<String> USER_SCHEMA = List.of(USER_SCHEMA_URN);

    private final UserService users;

    /**
     * 查询 SCIM 用户资源列表，支持常用 filter 和 1-based 分页参数。
     */
    @Operation(summary = "查询 SCIM 用户列表", description = "支持 filter、startIndex、count 参数，filter 支持 userName、displayName、active、emails.value、phoneNumbers.value 的 eq/co/sw 操作。")
    @GetMapping
    ScimListResponse<ScimUserResponse> list(
        @Parameter(description = "SCIM 过滤表达式，例如 userName eq \"alice\" 或 emails.value co \"example.com\"")
        @RequestParam(required = false) String filter,
        @Parameter(description = "分页起始序号，SCIM 规范使用 1-based，默认 1")
        @RequestParam(required = false) Integer startIndex,
        @Parameter(description = "返回数量，默认返回过滤后的全部资源")
        @RequestParam(required = false) Integer count
    ) {
        List<ScimUserResponse> resources = users.list().stream()
            .map(this::toScimUser)
            .toList();
        return ScimQuerySupport.listResponse(resources, filter, startIndex, count, this::supportsFilter, this::matchesFilter);
    }

    /**
     * 按内部 UUID 查询单个 SCIM 用户资源。
     */
    @Operation(summary = "获取 SCIM 用户详情", description = "根据用户 UUID 返回 SCIM User 资源。")
    @GetMapping("/{userId}")
    ScimUserResponse get(@Parameter(description = "用户 UUID") @PathVariable UUID userId) {
        return toScimUser(users.get(userId));
    }

    /**
     * 创建 SCIM 用户，并映射为内部用户账号。
     */
    @Operation(summary = "创建 SCIM 用户", description = "根据 SCIM User 请求创建内部用户账号。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScimUserResponse create(
        @Parameter(description = "SCIM User 创建请求") @Valid @RequestBody CreateScimUserRequest request,
        Principal principal
    ) {
        String displayName = displayName(request);
        String email = primaryEmail(request);
        String mobile = primaryPhoneNumber(request);
        UserResponse user = users.createScimUser(request.userName(), displayName, email, mobile, principal.getName());
        if (Boolean.FALSE.equals(request.active())) {
            user = users.suspend(user.id(), principal.getName());
        }
        return toScimUser(user);
    }

    /**
     * 使用完整 SCIM User 资源替换用户资料和启用状态。
     */
    @Operation(summary = "替换 SCIM 用户", description = "以请求体整体替换用户显示名、邮箱、手机号和启用状态；userName 不可修改。")
    @PutMapping("/{userId}")
    ScimUserResponse replace(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "SCIM User 资源") @Valid @RequestBody CreateScimUserRequest request,
        Principal principal
    ) {
        UserResponse current = users.get(userId);
        if (!current.username().equals(request.userName())) {
            throw new IllegalArgumentException("SCIM 不支持修改 userName");
        }
        return toScimUser(users.updateScimUser(
            userId,
            displayName(request),
            primaryEmail(request),
            primaryPhoneNumber(request),
            request.active() == null ? Boolean.TRUE : request.active(),
            principal.getName()));
    }

    /**
     * 按 SCIM PATCH 操作局部更新用户。
     */
    @Operation(summary = "局部更新 SCIM 用户", description = "支持 add/replace/remove 操作 active、displayName、name.formatted、emails、phoneNumbers；未识别的属性会被忽略。")
    @PatchMapping("/{userId}")
    ScimUserResponse patch(
        @Parameter(description = "用户 UUID") @PathVariable UUID userId,
        @Parameter(description = "SCIM PatchOp 请求") @Valid @RequestBody ScimPatchRequest request,
        Principal principal
    ) {
        UserResponse current = users.get(userId);
        PatchState state = new PatchState(current.displayName(), current.email(), current.mobile(), null);
        request.operations().forEach(operation -> applyPatch(state, operation, current.username()));
        return toScimUser(users.updateScimUser(userId, state.displayName, state.email, state.mobile, state.active, principal.getName()));
    }

    /**
     * 删除 SCIM 用户。
     */
    @Operation(summary = "删除 SCIM 用户", description = "删除内部用户账号及其凭据、令牌和授权。")
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(description = "用户 UUID") @PathVariable UUID userId, Principal principal) {
        users.delete(userId, principal.getName());
    }

    private void applyPatch(PatchState state, ScimPatchOperation operation, String username) {
        String op = ScimPatchSupport.op(operation);
        String path = ScimPatchSupport.path(operation.path(), USER_SCHEMA_URN);
        if (path == null) {
            if (op.equals("remove")) {
                throw new IllegalArgumentException("SCIM remove operation requires a path");
            }
            for (Map.Entry<?, ?> entry : ScimPatchSupport.attributes(operation).entrySet()) {
                String attribute = ScimPatchSupport.path(String.valueOf(entry.getKey()), USER_SCHEMA_URN);
                applyAttribute(state, op, attribute, entry.getValue(), username);
            }
            return;
        }
        applyAttribute(state, op, path, operation.value(), username);
    }

    private void applyAttribute(PatchState state, String op, String path, Object value, String username) {
        boolean remove = op.equals("remove");
        if (path.equals("active")) {
            state.active = remove ? Boolean.FALSE : ScimPatchSupport.bool(value);
        } else if (path.equals("displayname") || path.equals("name.formatted")) {
            state.displayName = remove ? null : ScimPatchSupport.text(value);
        } else if (path.equals("name")) {
            state.displayName = remove || !(value instanceof Map<?, ?> name) ? null : ScimPatchSupport.text(name.get("formatted"));
        } else if (path.equals("emails") || path.startsWith("emails[") || path.startsWith("emails.")) {
            state.email = remove ? null : ScimPatchSupport.text(value);
        } else if (path.equals("phonenumbers") || path.startsWith("phonenumbers[") || path.startsWith("phonenumbers.")) {
            state.mobile = remove ? null : ScimPatchSupport.text(value);
        } else if (path.equals("username")) {
            if (remove || !username.equals(ScimPatchSupport.text(value))) {
                throw new IllegalArgumentException("SCIM 不支持修改 userName");
            }
        }
    }

    private static final class PatchState {
        private String displayName;
        private String email;
        private String mobile;
        private Boolean active;

        private PatchState(String displayName, String email, String mobile, Boolean active) {
            this.displayName = displayName;
            this.email = email;
            this.mobile = mobile;
            this.active = active;
        }
    }

    private ScimUserResponse toScimUser(UserResponse user) {
        ScimEmail email = user.email() == null ? null : new ScimEmail(user.email(), "work", true);
        ScimPhoneNumber phone = user.mobile() == null ? null : new ScimPhoneNumber(user.mobile(), "work", true);
        return new ScimUserResponse(
            USER_SCHEMA,
            user.id().toString(),
            user.username(),
            user.displayName(),
            new ScimName(user.displayName(), null, null),
            email == null ? List.of() : List.of(email),
            phone == null ? List.of() : List.of(phone),
            user.status() == com.antiam.domain.AccountStatus.ACTIVE);
    }

    private String displayName(CreateScimUserRequest request) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            return request.displayName();
        }
        if (request.name() != null && request.name().formatted() != null && !request.name().formatted().isBlank()) {
            return request.name().formatted();
        }
        return request.userName();
    }

    private String primaryEmail(CreateScimUserRequest request) {
        if (request.emails() == null || request.emails().isEmpty()) {
            return null;
        }
        return request.emails().stream()
            .filter(email -> Boolean.TRUE.equals(email.primary()))
            .findFirst()
            .orElse(request.emails().getFirst())
            .value();
    }

    private String primaryPhoneNumber(CreateScimUserRequest request) {
        if (request.phoneNumbers() == null || request.phoneNumbers().isEmpty()) {
            return null;
        }
        return request.phoneNumbers().stream()
            .filter(phone -> Boolean.TRUE.equals(phone.primary()))
            .findFirst()
            .orElse(request.phoneNumbers().getFirst())
            .value();
    }

    private boolean supportsFilter(ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "userName", "displayName", "active", "emails.value", "phoneNumbers.value" -> true;
            default -> false;
        };
    }

    private boolean matchesFilter(ScimUserResponse user, ScimQuerySupport.ScimFilter filter) {
        return switch (filter.attribute()) {
            case "userName" -> filter.matches(user.userName());
            case "displayName" -> filter.matches(user.displayName());
            case "active" -> filter.matches(String.valueOf(user.active()));
            case "emails.value" -> user.emails().stream().anyMatch(email -> filter.matches(email.value()));
            case "phoneNumbers.value" -> user.phoneNumbers().stream().anyMatch(phone -> filter.matches(phone.value()));
            default -> false;
        };
    }
}
