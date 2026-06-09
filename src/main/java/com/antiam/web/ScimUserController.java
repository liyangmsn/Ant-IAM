package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimUserRequest;
import static com.antiam.dto.ScimDtos.ScimEmail;
import static com.antiam.dto.ScimDtos.ScimListResponse;
import static com.antiam.dto.ScimDtos.ScimName;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private static final List<String> USER_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:User");

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
        return toScimUser(user);
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
