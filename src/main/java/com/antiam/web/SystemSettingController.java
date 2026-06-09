package com.antiam.web;

import static com.antiam.dto.SettingDtos.SettingResponse;
import static com.antiam.dto.SettingDtos.UpsertSettingRequest;

import com.antiam.domain.SettingValueType;
import com.antiam.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "系统配置", description = "全局系统配置项查询、保存和删除接口")
public class SystemSettingController {

    private final SystemSettingService settings;

    /**
     * 查询系统配置列表。
     */
    @Operation(summary = "查询系统配置", description = "查询全局配置项，支持分类、值类型、敏感标记和关键字过滤。")
    @GetMapping
    List<SettingResponse> list(
        @Parameter(description = "配置分类") @RequestParam(required = false) String category,
        @Parameter(description = "配置值类型") @RequestParam(required = false) SettingValueType valueType,
        @Parameter(description = "是否敏感配置") @RequestParam(required = false) Boolean sensitive,
        @Parameter(description = "关键字，匹配配置键、名称或值") @RequestParam(required = false) String keyword
    ) {
        return settings.list(category, valueType, sensitive, keyword);
    }

    /**
     * 查询单个系统配置。
     */
    @Operation(summary = "获取系统配置", description = "根据配置键返回全局配置详情。")
    @GetMapping("/{settingKey}")
    SettingResponse get(@Parameter(description = "配置键") @PathVariable String settingKey) {
        return settings.get(settingKey);
    }

    /**
     * 新增或更新系统配置。
     */
    @Operation(summary = "保存系统配置", description = "按配置键新增或更新全局配置。")
    @PostMapping
    SettingResponse upsert(
        @Parameter(description = "系统配置保存请求") @Valid @RequestBody UpsertSettingRequest request,
        Principal principal
    ) {
        return settings.upsert(request, principal.getName());
    }

    /**
     * 删除系统配置。
     */
    @Operation(summary = "删除系统配置", description = "删除指定配置键对应的全局配置。")
    @DeleteMapping("/{settingKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@Parameter(description = "配置键") @PathVariable String settingKey, Principal principal) {
        settings.delete(settingKey, principal.getName());
    }
}
