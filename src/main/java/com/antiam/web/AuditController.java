package com.antiam.web;

import static com.antiam.dto.AuditDtos.AuditEventListResponse;
import static com.antiam.dto.AuditDtos.AuditEventResponse;

import com.antiam.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计事件查询、导出和详情接口")
public class AuditController {

    private final AuditService auditService;

    /**
     * 查询审计事件列表。
     */
    @Operation(summary = "查询审计事件", description = "按操作者、动作、目标、时间范围和关键字检索审计事件。")
    @GetMapping
    AuditEventListResponse search(
        @Parameter(description = "操作者账号") @RequestParam(required = false) String actor,
        @Parameter(description = "审计动作") @RequestParam(required = false) String action,
        @Parameter(description = "目标资源类型") @RequestParam(required = false) String targetType,
        @Parameter(description = "目标资源 ID") @RequestParam(required = false) String targetId,
        @Parameter(description = "开始时间，ISO-8601 格式") @RequestParam(required = false) Instant from,
        @Parameter(description = "结束时间，ISO-8601 格式") @RequestParam(required = false) Instant to,
        @Parameter(description = "关键字，匹配审计详情") @RequestParam(required = false) String keyword,
        @Parameter(description = "返回数量上限") @RequestParam(defaultValue = "100") int limit
    ) {
        return auditService.search(actor, action, targetType, targetId, from, to, keyword, limit);
    }

    /**
     * 导出审计事件 CSV。
     */
    @Operation(summary = "导出审计事件", description = "按查询条件导出审计事件 CSV 文件。")
    @GetMapping(value = "/export", produces = "text/csv")
    ResponseEntity<String> exportCsv(
        @Parameter(description = "操作者账号") @RequestParam(required = false) String actor,
        @Parameter(description = "审计动作") @RequestParam(required = false) String action,
        @Parameter(description = "目标资源类型") @RequestParam(required = false) String targetType,
        @Parameter(description = "目标资源 ID") @RequestParam(required = false) String targetId,
        @Parameter(description = "开始时间，ISO-8601 格式") @RequestParam(required = false) Instant from,
        @Parameter(description = "结束时间，ISO-8601 格式") @RequestParam(required = false) Instant to,
        @Parameter(description = "关键字，匹配审计详情") @RequestParam(required = false) String keyword,
        @Parameter(description = "导出数量上限") @RequestParam(defaultValue = "500") int limit
    ) {
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-events.csv\"")
            .body(auditService.exportCsv(actor, action, targetType, targetId, from, to, keyword, limit));
    }

    /**
     * 查询单条审计事件详情。
     */
    @Operation(summary = "获取审计事件详情", description = "根据审计事件 UUID 返回事件详情。")
    @GetMapping("/{auditEventId}")
    AuditEventResponse get(@Parameter(description = "审计事件 UUID") @PathVariable UUID auditEventId) {
        return auditService.get(auditEventId);
    }
}
