package com.antiam.web;

import static com.antiam.dto.PolicyDtos.AuthenticationPolicyResponse;
import static com.antiam.dto.PolicyDtos.AuthenticationPolicyDecisionResponse;
import static com.antiam.dto.PolicyDtos.CreateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.EvaluateAuthenticationPolicyRequest;
import static com.antiam.dto.PolicyDtos.UpdateAuthenticationPolicyRequest;

import com.antiam.service.AuthenticationPolicyService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authentication-policies")
@RequiredArgsConstructor
@Tag(name = "认证策略", description = "认证策略生命周期、动作和策略评估接口")
public class AuthenticationPolicyController {

    private final AuthenticationPolicyService policies;

    /**
     * 查询认证策略列表。
     */
    @Operation(summary = "查询认证策略", description = "返回全部认证策略及其匹配条件和动作。")
    @GetMapping
    List<AuthenticationPolicyResponse> list() {
        return policies.list();
    }

    /**
     * 创建认证策略。
     */
    @Operation(summary = "创建认证策略", description = "创建登录认证策略，可配置匹配条件、优先级和执行动作。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AuthenticationPolicyResponse create(
        @Parameter(description = "认证策略创建请求") @Valid @RequestBody CreateAuthenticationPolicyRequest request,
        Principal principal
    ) {
        return policies.create(request, principal.getName());
    }

    /**
     * 更新认证策略。
     */
    @Operation(summary = "更新认证策略", description = "更新认证策略名称、优先级、条件和动作。")
    @PutMapping("/{policyId}")
    AuthenticationPolicyResponse update(
        @Parameter(description = "认证策略 UUID") @PathVariable UUID policyId,
        @Parameter(description = "认证策略更新请求") @Valid @RequestBody UpdateAuthenticationPolicyRequest request,
        Principal principal
    ) {
        return policies.update(policyId, request, principal.getName());
    }

    /**
     * 启用认证策略。
     */
    @Operation(summary = "启用认证策略", description = "启用指定认证策略，使其参与策略评估。")
    @PostMapping("/{policyId}/enable")
    AuthenticationPolicyResponse enable(@Parameter(description = "认证策略 UUID") @PathVariable UUID policyId, Principal principal) {
        return policies.enable(policyId, principal.getName());
    }

    /**
     * 停用认证策略。
     */
    @Operation(summary = "停用认证策略", description = "停用指定认证策略。")
    @PostMapping("/{policyId}/disable")
    AuthenticationPolicyResponse disable(@Parameter(description = "认证策略 UUID") @PathVariable UUID policyId, Principal principal) {
        return policies.disable(policyId, principal.getName());
    }

    /**
     * 执行认证策略评估。
     */
    @Operation(summary = "评估认证策略", description = "根据用户、应用、风险等级和上下文返回应执行的认证动作。")
    @PostMapping("/evaluations")
    AuthenticationPolicyDecisionResponse evaluate(
        @Parameter(description = "认证策略评估请求") @Valid @RequestBody EvaluateAuthenticationPolicyRequest request
    ) {
        return policies.evaluate(request);
    }
}
