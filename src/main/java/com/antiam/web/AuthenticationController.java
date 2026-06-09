package com.antiam.web;

import static com.antiam.dto.AuthenticationDtos.AuthenticationEventResponse;
import static com.antiam.dto.AuthenticationDtos.AuthenticationSessionResponse;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationEventRequest;
import static com.antiam.dto.AuthenticationDtos.CreateAuthenticationSessionRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsRequest;
import static com.antiam.dto.AuthenticationDtos.EndAuthenticationSessionsResponse;

import com.antiam.domain.AuthenticationEventType;
import com.antiam.service.AuthenticationService;
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
@RequestMapping("/api/v1/authentication")
@RequiredArgsConstructor
@Tag(name = "认证会话", description = "认证会话、登录事件和会话结束管理接口")
public class AuthenticationController {

    private final AuthenticationService authentication;

    /**
     * 查询认证会话列表，可按用户、应用和活跃状态过滤。
     */
    @Operation(summary = "查询认证会话", description = "查询登录会话记录，支持按用户、应用和是否活跃过滤。")
    @GetMapping("/sessions")
    List<AuthenticationSessionResponse> sessions(
        @Parameter(description = "用户 UUID，不传则查询全部用户") @RequestParam(required = false) UUID userId,
        @Parameter(description = "应用 UUID，不传则查询全部应用") @RequestParam(required = false) UUID applicationId,
        @Parameter(description = "是否仅查询活跃会话") @RequestParam(required = false) Boolean active
    ) {
        return authentication.listSessions(userId, applicationId, active);
    }

    /**
     * 创建认证会话，通常由登录流程在认证成功后调用。
     */
    @Operation(summary = "创建认证会话", description = "为用户和可选应用创建认证会话，并记录客户端、IP 和过期时间等信息。")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    AuthenticationSessionResponse createSession(
        @Parameter(description = "认证会话创建请求") @Valid @RequestBody CreateAuthenticationSessionRequest request,
        Principal principal
    ) {
        return authentication.createSession(request, principal.getName());
    }

    /**
     * 结束单个认证会话。
     */
    @Operation(summary = "结束认证会话", description = "结束指定认证会话，使其不再作为活跃会话使用。")
    @PostMapping("/sessions/{sessionId}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void endSession(@Parameter(description = "认证会话 UUID") @PathVariable UUID sessionId, Principal principal) {
        authentication.endSession(sessionId, principal.getName());
    }

    /**
     * 批量结束认证会话。
     */
    @Operation(summary = "批量结束认证会话", description = "按用户、应用或请求条件批量结束认证会话。")
    @PostMapping("/sessions/end")
    EndAuthenticationSessionsResponse endSessions(
        @Parameter(description = "批量结束会话请求") @RequestBody EndAuthenticationSessionsRequest request,
        Principal principal
    ) {
        return authentication.endSessions(request, principal.getName());
    }

    /**
     * 查询认证事件流水。
     */
    @Operation(summary = "查询认证事件", description = "查询登录、登出、失败、MFA 等认证事件，支持多维度过滤。")
    @GetMapping("/events")
    List<AuthenticationEventResponse> events(
        @Parameter(description = "认证事件类型") @RequestParam(required = false) AuthenticationEventType type,
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "应用 UUID") @RequestParam(required = false) UUID applicationId,
        @Parameter(description = "认证会话 UUID") @RequestParam(required = false) UUID sessionId,
        @Parameter(description = "客户端 IP 地址") @RequestParam(required = false) String ipAddress,
        @Parameter(description = "关键字，匹配用户名、应用或事件详情") @RequestParam(required = false) String keyword
    ) {
        return authentication.recentEvents(type, userId, applicationId, sessionId, ipAddress, keyword);
    }

    /**
     * 记录认证事件。
     */
    @Operation(summary = "记录认证事件", description = "写入认证相关事件，用于审计、风险分析和会话追踪。")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    AuthenticationEventResponse recordEvent(
        @Parameter(description = "认证事件创建请求") @Valid @RequestBody CreateAuthenticationEventRequest request,
        Principal principal
    ) {
        return authentication.recordEvent(request, principal.getName());
    }
}
