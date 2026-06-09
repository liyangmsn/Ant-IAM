package com.antiam.web;

import static com.antiam.dto.RiskDtos.CreateRiskRuleRequest;
import static com.antiam.dto.RiskDtos.EvaluateRiskRequest;
import static com.antiam.dto.RiskDtos.RiskAssessmentResponse;
import static com.antiam.dto.RiskDtos.RiskRuleResponse;
import static com.antiam.dto.RiskDtos.UpdateRiskRuleRequest;

import com.antiam.domain.RiskLevel;
import com.antiam.domain.RiskRuleType;
import com.antiam.service.RiskService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
@Tag(name = "风险控制", description = "风险规则、风险评估和风险决策查询接口")
public class RiskController {

    private final RiskService risk;

    /**
     * 查询风险规则列表。
     */
    @Operation(summary = "查询风险规则", description = "按规则类型、启用状态和关键字查询风险规则。")
    @GetMapping("/rules")
    List<RiskRuleResponse> rules(
        @Parameter(description = "风险规则类型") @RequestParam(required = false) RiskRuleType type,
        @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled,
        @Parameter(description = "关键字，匹配规则编码、名称或配置") @RequestParam(required = false) String keyword
    ) {
        return risk.listRules(type, enabled, keyword);
    }

    /**
     * 创建风险规则。
     */
    @Operation(summary = "创建风险规则", description = "创建用于登录或访问风险判断的规则。")
    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    RiskRuleResponse createRule(
        @Parameter(description = "风险规则创建请求") @Valid @RequestBody CreateRiskRuleRequest request,
        Principal principal
    ) {
        return risk.createRule(request, principal.getName());
    }

    /**
     * 查询风险规则详情。
     */
    @Operation(summary = "获取风险规则详情", description = "根据风险规则 UUID 返回规则详情。")
    @GetMapping("/rules/{ruleId}")
    RiskRuleResponse rule(@Parameter(description = "风险规则 UUID") @PathVariable UUID ruleId) {
        return risk.getRule(ruleId);
    }

    /**
     * 更新风险规则。
     */
    @Operation(summary = "更新风险规则", description = "更新风险规则名称、类型、风险等级、决策和配置。")
    @PutMapping("/rules/{ruleId}")
    RiskRuleResponse updateRule(
        @Parameter(description = "风险规则 UUID") @PathVariable UUID ruleId,
        @Parameter(description = "风险规则更新请求") @Valid @RequestBody UpdateRiskRuleRequest request,
        Principal principal
    ) {
        return risk.updateRule(ruleId, request, principal.getName());
    }

    /**
     * 启用风险规则。
     */
    @Operation(summary = "启用风险规则", description = "启用指定风险规则，使其参与风险评估。")
    @PostMapping("/rules/{ruleId}/enable")
    RiskRuleResponse enableRule(@Parameter(description = "风险规则 UUID") @PathVariable UUID ruleId, Principal principal) {
        return risk.enableRule(ruleId, principal.getName());
    }

    /**
     * 停用风险规则。
     */
    @Operation(summary = "停用风险规则", description = "停用指定风险规则。")
    @PostMapping("/rules/{ruleId}/disable")
    RiskRuleResponse disableRule(@Parameter(description = "风险规则 UUID") @PathVariable UUID ruleId, Principal principal) {
        return risk.disableRule(ruleId, principal.getName());
    }

    /**
     * 执行一次风险评估。
     */
    @Operation(summary = "创建风险评估", description = "根据用户、IP、设备、地理位置和上下文计算风险等级与决策。")
    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    RiskAssessmentResponse evaluate(@Parameter(description = "风险评估请求") @Valid @RequestBody EvaluateRiskRequest request) {
        return risk.evaluate(request);
    }

    /**
     * 查询风险评估记录。
     */
    @Operation(summary = "查询风险评估", description = "按用户、风险等级、决策和关键字查询风险评估历史。")
    @GetMapping("/assessments")
    List<RiskAssessmentResponse> assessments(
        @Parameter(description = "用户 UUID") @RequestParam(required = false) UUID userId,
        @Parameter(description = "风险等级") @RequestParam(required = false) RiskLevel riskLevel,
        @Parameter(description = "风险决策，例如 ALLOW、CHALLENGE 或 DENY") @RequestParam(required = false) String decision,
        @Parameter(description = "关键字，匹配 IP、设备或上下文") @RequestParam(required = false) String keyword,
        @Parameter(description = "返回数量上限") @RequestParam(required = false) Integer limit
    ) {
        return risk.searchAssessments(userId, riskLevel, decision, keyword, limit);
    }

    /**
     * 查询风险评估详情。
     */
    @Operation(summary = "获取风险评估详情", description = "根据风险评估 UUID 返回评估详情。")
    @GetMapping("/assessments/{assessmentId}")
    RiskAssessmentResponse assessment(@Parameter(description = "风险评估 UUID") @PathVariable UUID assessmentId) {
        return risk.getAssessment(assessmentId);
    }
}
