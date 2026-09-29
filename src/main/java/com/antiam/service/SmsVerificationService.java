package com.antiam.service;

import static com.antiam.dto.AuthenticationDtos.SendSmsCodeResponse;

import com.antiam.common.AuthenticationFailedException;
import com.antiam.repository.SystemSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.sms4j.aliyun.config.AlibabaConfig;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.qiniu.config.QiNiuConfig;
import org.dromara.sms4j.tencent.config.TencentConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("\\d{4,32}");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?\\d{6,20}$");
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final long RESEND_INTERVAL_SECONDS = 60;
    private static final String CAPTCHA_TTL_SETTING_KEY = "security.general.captcha_ttl_minutes";

    private static final String SERVICE_SETTING_KEY = "message.sms.service";
    private static final String CONFIGURED_BLEND_ID = "iam-configured-sms";
    private static final String DEFAULT_TEMPLATE_TYPE = "登录验证";
    // 验证码用途与短信服务配置中“发送场景”模板类型的对应关系。
    private static final Map<String, String> PURPOSE_TEMPLATE_TYPES = Map.of(
        "LOGIN", "登录验证",
        "BIND_MOBILE", "绑定手机号",
        "CHANGE_MOBILE", "修改手机号",
        "FORGOT_PASSWORD", "忘记密码",
        "CHANGE_PASSWORD", "修改密码",
        "MFA", "登录验证");

    private final Map<VerificationKey, VerificationCode> codes = new ConcurrentHashMap<>();
    private final SystemSettingRepository settings;
    private final ObjectMapper objectMapper;
    private String configuredBlendSignature;

    @Value("${iam.sms.blend-id:fixed-code}")
    private String smsBlendId;

    @Value("${iam.sms.code-ttl-seconds:300}")
    private long defaultCodeTtlSeconds;

    public SendSmsCodeResponse sendVerificationCode(String mobile, String purpose) {
        String normalizedMobile = normalizeMobile(mobile);
        String normalizedPurpose = normalizePurpose(purpose);
        VerificationKey key = new VerificationKey(normalizedMobile, normalizedPurpose);
        Instant now = Instant.now();
        VerificationCode previous = codes.get(key);
        if (previous != null && previous.sentAt().plusSeconds(RESEND_INTERVAL_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }
        String code = deliverCode(normalizedMobile, normalizedPurpose);
        Instant expiresAt = now.plusSeconds(codeTtlSeconds());
        codes.put(key, new VerificationCode(code, now, expiresAt, new AtomicInteger()));
        return new SendSmsCodeResponse(normalizedMobile, normalizedPurpose, expiresAt);
    }

    /**
     * 向手机号发送一次性验证码并返回实际下发的验证码，不在本服务内保存，供 MFA 挑战等调用方自行校验。
     */
    public String deliverCode(String mobile, String purpose) {
        String normalizedMobile = normalizeMobile(mobile);
        String normalizedPurpose = normalizePurpose(purpose);
        String generatedCode = generateCode();
        SmsResponse response = deliver(normalizedMobile, normalizedPurpose, generatedCode);
        return resolveCode(response, generatedCode);
    }

    /**
     * 当前验证码有效期（秒），优先使用通用安全设置中的验证码有效期。
     */
    public long codeTtlSeconds() {
        return settings.findBySettingKey(CAPTCHA_TTL_SETTING_KEY)
            .map(setting -> setting.getSettingValue())
            .filter(value -> value != null && value.matches("\\d{1,6}"))
            .map(Long::parseLong)
            .filter(minutes -> minutes > 0)
            .map(minutes -> minutes * 60)
            .orElse(defaultCodeTtlSeconds);
    }

    public void verify(String mobile, String purpose, String code) {
        String normalizedMobile = normalizeMobile(mobile);
        String normalizedPurpose = normalizePurpose(purpose);
        VerificationKey key = new VerificationKey(normalizedMobile, normalizedPurpose);
        VerificationCode verificationCode = codes.get(key);
        if (verificationCode == null) {
            throw new AuthenticationFailedException("SMS verification code is invalid");
        }
        if (verificationCode.isExpired(Instant.now())) {
            codes.remove(key);
            throw new AuthenticationFailedException("SMS verification code is invalid");
        }
        if (code == null || !code.trim().equals(verificationCode.code())) {
            // 超过最大尝试次数后验证码作废，防止暴力枚举。
            if (verificationCode.attempts().incrementAndGet() >= MAX_VERIFY_ATTEMPTS) {
                codes.remove(key);
            }
            throw new AuthenticationFailedException("SMS verification code is invalid");
        }
        codes.remove(key);
    }

    /**
     * 使用系统设置中的短信服务向指定手机号发送一条测试验证码，不保存验证码。
     */
    public String sendTest(String mobile, String templateType) {
        String normalizedMobile = normalizeMobile(mobile);
        JsonNode config = configuredService()
            .orElseThrow(() -> new IllegalArgumentException("短信服务未启用，请先开启并保存短信服务配置"));
        String type = templateType == null || templateType.isBlank() ? DEFAULT_TEMPLATE_TYPE : templateType.trim();
        SmsResponse response = sendWithConfig(config, normalizedMobile, type, generateCode());
        if (response == null || !response.isSuccess()) {
            throw new IllegalArgumentException("测试短信发送失败：" + (response == null ? "无响应" : String.valueOf(response.getData())));
        }
        return "测试短信已发送至 " + normalizedMobile;
    }

    private SmsResponse deliver(String mobile, String purpose, String code) {
        Optional<JsonNode> config = configuredService();
        SmsResponse response = config.isPresent()
            ? sendWithConfig(config.get(), mobile, PURPOSE_TEMPLATE_TYPES.getOrDefault(purpose, DEFAULT_TEMPLATE_TYPE), code)
            : smsBlend().sendMessage(mobile, code);
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("SMS verification code delivery failed");
        }
        return response;
    }

    // 读取系统设置中的短信服务；未配置或未启用时回退到 iam.sms.blend-id 指定的通道。
    private Optional<JsonNode> configuredService() {
        return settings.findBySettingKey(SERVICE_SETTING_KEY)
            .map(setting -> readJson(setting.getSettingValue()))
            .filter(node -> node.path("enabled").asBoolean(false));
    }

    private SmsResponse sendWithConfig(JsonNode config, String mobile, String templateType, String code) {
        String templateId = text(config.path("templates"), templateType);
        if (templateId == null) {
            templateId = text(config.path("templates"), DEFAULT_TEMPLATE_TYPE);
        }
        if (templateId == null) {
            throw new IllegalArgumentException("短信模板未配置：" + templateType);
        }
        LinkedHashMap<String, String> variables = new LinkedHashMap<>();
        variables.put("code", code);
        variables.put("time", String.valueOf(Math.max(1, codeTtlSeconds() / 60)));
        return configuredBlend(config).sendMessage(mobile, templateId, variables);
    }

    private synchronized SmsBlend configuredBlend(JsonNode config) {
        String signature = config.toString();
        SmsBlend blend = SmsFactory.getSmsBlend(CONFIGURED_BLEND_ID);
        if (blend != null && signature.equals(configuredBlendSignature)) {
            return blend;
        }
        if (blend != null) {
            SmsFactory.unregister(CONFIGURED_BLEND_ID);
        }
        SmsFactory.createSmsBlend(supplierConfig(config));
        configuredBlendSignature = signature;
        blend = SmsFactory.getSmsBlend(CONFIGURED_BLEND_ID);
        if (blend == null) {
            throw new IllegalStateException("SMS channel could not be created: " + CONFIGURED_BLEND_ID);
        }
        return blend;
    }

    private BaseConfig supplierConfig(JsonNode config) {
        String provider = text(config, "provider");
        BaseConfig supplier;
        if ("tencent".equals(provider)) {
            TencentConfig tencent = new TencentConfig();
            tencent.setAccessKeyId(required(config, "secretId"));
            tencent.setAccessKeySecret(required(config, "secretKey"));
            tencent.setSdkAppId(required(config, "sdkAppId"));
            tencent.setSignature(required(config, "signature"));
            String region = text(config, "region");
            if (region != null) {
                tencent.setTerritory(region);
            }
            supplier = tencent;
        } else if ("qiniu".equals(provider)) {
            QiNiuConfig qiniu = new QiNiuConfig();
            qiniu.setAccessKeyId(required(config, "accessKey"));
            qiniu.setAccessKeySecret(required(config, "secretKey"));
            supplier = qiniu;
        } else {
            AlibabaConfig aliyun = new AlibabaConfig();
            aliyun.setAccessKeyId(required(config, "accessKeyId"));
            aliyun.setAccessKeySecret(required(config, "accessKeySecret"));
            aliyun.setSignature(required(config, "signature"));
            supplier = aliyun;
        }
        supplier.setConfigId(CONFIGURED_BLEND_ID);
        return supplier;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("SMS setting value must be valid JSON", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            throw new IllegalArgumentException("短信服务字段未配置：" + field);
        }
        return value;
    }

    private SmsBlend smsBlend() {
        SmsBlend blend = smsBlendId == null || smsBlendId.isBlank()
            ? SmsFactory.getSmsBlend()
            : SmsFactory.getSmsBlend(smsBlendId);
        if (blend == null) {
            throw new IllegalStateException("SMS channel is not configured: " + smsBlendId);
        }
        return blend;
    }

    private String resolveCode(SmsResponse response, String generatedCode) {
        Object data = response.getData();
        if (data instanceof String value && isVerificationCode(value)) {
            return value;
        }
        if (data instanceof Map<?, ?> values) {
            Object code = values.get("code");
            if (code instanceof String value && isVerificationCode(value)) {
                return value;
            }
        }
        return generatedCode;
    }

    private boolean isVerificationCode(String value) {
        return VERIFICATION_CODE_PATTERN.matcher(value).matches();
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    /**
     * 规范化并校验手机号格式。
     */
    public static String normalizeMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            throw new IllegalArgumentException("Mobile is required");
        }
        String normalized = mobile.trim().replace(" ", "").replace("-", "");
        if (!MOBILE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return normalized;
    }

    private String normalizePurpose(String purpose) {
        String normalized = purpose == null || purpose.isBlank() ? "LOGIN" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!PURPOSE_TEMPLATE_TYPES.containsKey(normalized)) {
            throw new IllegalArgumentException("不支持的验证码用途：" + purpose);
        }
        return normalized;
    }

    private record VerificationKey(String mobile, String purpose) {
    }

    private record VerificationCode(String code, Instant sentAt, Instant expiresAt, AtomicInteger attempts) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
