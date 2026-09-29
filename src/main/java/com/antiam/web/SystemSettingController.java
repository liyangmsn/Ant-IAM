package com.antiam.web;

import static com.antiam.dto.SettingDtos.GeoIpUpdateRequest;
import static com.antiam.dto.SettingDtos.IntegrationTestResponse;
import static com.antiam.dto.SettingDtos.MailTestRequest;
import static com.antiam.dto.SettingDtos.SettingResponse;
import static com.antiam.dto.SettingDtos.SmsTestRequest;
import static com.antiam.dto.SettingDtos.UpsertSettingRequest;

import com.antiam.domain.SettingValueType;
import com.antiam.service.AuditService;
import com.antiam.service.GeoIpService;
import com.antiam.service.GeoIpService.GeoIpLocation;
import com.antiam.service.MailDeliveryService;
import com.antiam.service.SmsVerificationService;
import com.antiam.service.SystemSettingService;
import com.antiam.service.storage.FileStorageService;
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
    private final MailDeliveryService mailDeliveryService;
    private final SmsVerificationService smsVerificationService;
    private final GeoIpService geoIpService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

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

    /**
     * 使用当前邮件服务配置发送测试邮件。
     */
    @Operation(summary = "发送测试邮件", description = "使用已保存的邮件服务配置和指定模板发送测试邮件。")
    @PostMapping("/message/mail/test")
    IntegrationTestResponse testMail(@Valid @RequestBody MailTestRequest request, Principal principal) {
        String message = mailDeliveryService.sendTest(request.to(), request.templateKey());
        auditService.record(principal.getName(), "system_setting.mail_test", "system_setting", "message.mail.service", request.to());
        return new IntegrationTestResponse(true, message);
    }

    /**
     * 使用当前短信服务配置发送测试短信。
     */
    @Operation(summary = "发送测试短信", description = "使用已保存的短信服务配置和指定发送场景模板发送测试短信。")
    @PostMapping("/message/sms/test")
    IntegrationTestResponse testSms(@Valid @RequestBody SmsTestRequest request, Principal principal) {
        String message = smsVerificationService.sendTest(request.mobile(), request.templateType());
        auditService.record(principal.getName(), "system_setting.sms_test", "system_setting", "message.sms.service", request.mobile());
        return new IntegrationTestResponse(true, message);
    }

    /**
     * 按当前 IP 地理库配置解析 IP。
     */
    @Operation(summary = "解析 IP 地理位置", description = "按当前 IP 地理库提供商解析指定 IP。")
    @GetMapping("/geo-ip/lookup")
    GeoIpLocation lookupGeoIp(@Parameter(description = "待解析 IP") @RequestParam String ip) {
        return geoIpService.lookup(ip);
    }

    /**
     * 下载并更新 MaxMind GeoLite2-City 数据库。
     */
    @Operation(summary = "更新 GeoIP 数据库", description = "使用 MaxMind License Key 下载 GeoLite2-City 数据库到指定路径。")
    @PostMapping("/geo-ip/update")
    IntegrationTestResponse updateGeoIp(@RequestBody GeoIpUpdateRequest request, Principal principal) {
        String message = geoIpService.updateDatabase(request.licenseKey(), request.databasePath());
        auditService.record(principal.getName(), "system_setting.geoip_update", "system_setting", "geoip.databasePath", request.databasePath());
        return new IntegrationTestResponse(true, message);
    }

    /**
     * 写入探测文件校验对象存储配置。
     */
    @Operation(summary = "校验对象存储", description = "使用已保存的对象存储配置写入探测文件。")
    @PostMapping("/storage/validate")
    IntegrationTestResponse validateStorage(Principal principal) {
        return new IntegrationTestResponse(true, fileStorageService.validate(principal.getName()));
    }
}
