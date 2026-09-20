package com.antiam.web;

import static com.antiam.dto.DashboardDtos.DashboardMetricsResponse;
import static com.antiam.dto.DashboardDtos.DashboardStatisticsResponse;
import static com.antiam.dto.DashboardDtos.DashboardSummaryResponse;
import static com.antiam.dto.DashboardDtos.RecentRiskAssessmentResponse;
import static com.antiam.dto.DashboardDtos.RecentSyncRunResponse;

import com.antiam.domain.DashboardRange;
import com.antiam.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "控制台概览", description = "IAM 控制台统计指标和近期动态接口")
public class DashboardController {

    private final DashboardService dashboard;

    /**
     * 查询控制台汇总数据。
     */
    @Operation(summary = "查询控制台汇总", description = "返回用户、组织、应用、风险等核心资源的概览统计。")
    @GetMapping("/summary")
    DashboardSummaryResponse summary() {
        return dashboard.summary();
    }

    /**
     * 查询控制台指标数据。
     */
    @Operation(summary = "查询控制台指标", description = "返回用于图表展示的关键运营指标。")
    @GetMapping("/metrics")
    DashboardMetricsResponse metrics() {
        return dashboard.metrics();
    }

    /**
     * 查询控制台图表统计。
     */
    @Operation(summary = "查询控制台统计", description = "按时间范围返回认证量趋势、应用访问排名、热门认证方式和登录位置分布。")
    @GetMapping("/statistics")
    DashboardStatisticsResponse statistics(@Parameter(description = "统计时间范围") @RequestParam DashboardRange range) {
        return dashboard.statistics(range);
    }

    /**
     * 查询近期风险评估。
     */
    @Operation(summary = "查询近期风险评估", description = "返回最近的登录或访问风险评估记录。")
    @GetMapping("/recent-risk-assessments")
    List<RecentRiskAssessmentResponse> recentRiskAssessments() {
        return dashboard.recentRiskAssessments();
    }

    /**
     * 查询近期身份源同步运行。
     */
    @Operation(summary = "查询近期同步运行", description = "返回最近的身份源同步任务运行记录。")
    @GetMapping("/recent-sync-runs")
    List<RecentSyncRunResponse> recentSyncRuns() {
        return dashboard.recentSyncRuns();
    }
}
