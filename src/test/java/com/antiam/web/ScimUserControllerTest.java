package com.antiam.web;

import static com.antiam.dto.ScimDtos.CreateScimUserRequest;
import static com.antiam.dto.ScimDtos.ScimPatchOperation;
import static com.antiam.dto.ScimDtos.ScimPatchRequest;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antiam.domain.AccountStatus;
import com.antiam.dto.UserDtos.UserResponse;
import com.antiam.service.UserService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScimUserControllerTest {

    private final UserService users = mock(UserService.class);
    private final ScimUserController controller = new ScimUserController(users);
    private final Principal principal = () -> "scim-client";
    private final UUID userId = UUID.randomUUID();
    private final UserResponse current = new UserResponse(
        userId, "zhangsan", "张三", "old@example.com", "13800000000", AccountStatus.ACTIVE,
        null, null, null, null, Set.of(), Set.of(), null);

    @BeforeEach
    void setUp() {
        when(users.get(userId)).thenReturn(current);
        when(users.updateScimUser(any(), any(), any(), any(), any(), anyString())).thenReturn(current);
    }

    @Test
    void patchAppliesPathAndPathlessOperations() {
        controller.patch(userId, new ScimPatchRequest(null, List.of(
            new ScimPatchOperation("Replace", "active", false),
            new ScimPatchOperation("replace", null, Map.of(
                "displayName", "张三丰",
                "emails", List.of(Map.of("value", "new@example.com", "primary", true)))),
            new ScimPatchOperation("remove", "phoneNumbers[type eq \"mobile\"]", null),
            new ScimPatchOperation("replace", "title", "工程师"))), principal);

        verify(users).updateScimUser(eq(userId), eq("张三丰"), eq("new@example.com"), isNull(), eq(false), eq("scim-client"));
    }

    @Test
    void patchRejectsUserNameChange() {
        ScimPatchRequest request = new ScimPatchRequest(null, List.of(new ScimPatchOperation("replace", "userName", "lisi")));

        assertThatThrownBy(() -> controller.patch(userId, request, principal))
            .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).updateScimUser(any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void putRejectsUserNameChangeAndDeleteDelegates() {
        CreateScimUserRequest request = new CreateScimUserRequest("lisi", "李四", null, null, null, true);

        assertThatThrownBy(() -> controller.replace(userId, request, principal))
            .isInstanceOf(IllegalArgumentException.class);

        controller.delete(userId, principal);
        verify(users).delete(userId, "scim-client");
    }
}
