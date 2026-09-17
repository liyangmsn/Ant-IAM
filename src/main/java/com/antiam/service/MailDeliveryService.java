package com.antiam.service;

import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailDeliveryService {

    private static final String SERVICE_SETTING_KEY = "message.mail.service";
    private static final String TEMPLATE_SETTING_PREFIX = "message.template.";
    private static final String CUSTOM_PROVIDER = "custom";

    private record ProviderDefaults(String host, int port) {
    }

    private static final Map<String, ProviderDefaults> PROVIDER_DEFAULTS = Map.of(
        "aliyun", new ProviderDefaults("smtp.qiye.aliyun.com", 465),
        "tencent", new ProviderDefaults("smtp.exmail.qq.com", 465),
        "netease", new ProviderDefaults("smtp.qiye.163.com", 465));

    private record DefaultTemplate(String subject, String content) {
    }

    private static final Map<String, DefaultTemplate> DEFAULT_TEMPLATES = Map.of(
        "bind_email", new DefaultTemplate("绑定邮箱验证码", "<p>您正在绑定邮箱，验证码为 ${code}，请尽快完成验证。</p>"),
        "change_email", new DefaultTemplate("修改绑定邮箱验证码", "<p>您正在修改绑定邮箱，验证码为 ${code}，请尽快完成验证。</p>"),
        "forgot_password", new DefaultTemplate("重置密码", "<p>您正在重置密码，重置凭证为 ${code}，请尽快完成操作。</p>"),
        "change_password", new DefaultTemplate("密码修改通知", "<p>您的账户密码已修改，如非本人操作请立即联系管理员。</p>"),
        "reset_password_success", new DefaultTemplate("重置密码成功", "<p>您的账户密码已重置成功。</p>"),
        "login_verify", new DefaultTemplate("登录验证码", "<p>您的登录验证码为 ${code}，请尽快完成验证。</p>"),
        "password_expiring", new DefaultTemplate("密码即将到期提醒", "<p>您的账户密码即将到期，请及时修改。</p>"),
        "welcome", new DefaultTemplate("欢迎使用 Ant IAM", "<p>欢迎加入 ${client_name}。</p>"));

    private record Template(String sender, String subject, String content) {
    }

    private final SystemSettingRepository settings;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * 按模板键渲染并投递邮件，模板变量以 ${name} 占位符形式替换。
     */
    public void send(String templateKey, String recipient, Map<String, String> variables) {
        DefaultTemplate fallback = DEFAULT_TEMPLATES.get(templateKey);
        if (fallback == null) {
            throw new IllegalArgumentException("Unknown mail template: " + templateKey);
        }
        JsonNode config = serviceConfig();
        Template template = resolveTemplate(templateKey, fallback);
        JavaMailSenderImpl sender = buildSender(config);
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress(config, template.sender()));
            helper.setTo(recipient);
            helper.setSubject(render(template.subject(), variables));
            helper.setText(render(template.content(), variables), true);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to build mail message for template: " + templateKey, ex);
        }
        sender.send(message);
        auditService.record("mail-delivery", "mail.send", "mail_template", templateKey, recipient);
    }

    private JsonNode serviceConfig() {
        SystemSetting setting = settings.findBySettingKey(SERVICE_SETTING_KEY)
            .orElseThrow(() -> new IllegalStateException("Mail service is not configured: " + SERVICE_SETTING_KEY));
        JsonNode config = readJson(setting.getSettingValue());
        if (!config.path("enabled").asBoolean(false)) {
            throw new IllegalStateException("Mail service is disabled: " + SERVICE_SETTING_KEY);
        }
        return config;
    }

    private Template resolveTemplate(String templateKey, DefaultTemplate fallback) {
        return settings.findBySettingKey(TEMPLATE_SETTING_PREFIX + templateKey)
            .map(setting -> readJson(setting.getSettingValue()))
            .filter(node -> node.path("customEnabled").asBoolean(false))
            .map(node -> new Template(
                text(node, "sender"),
                required(text(node, "subject"), "subject"),
                required(text(node, "content"), "content")))
            .orElseGet(() -> new Template(null, fallback.subject(), fallback.content()));
    }

    private JavaMailSenderImpl buildSender(JsonNode config) {
        String provider = text(config, "provider");
        ProviderDefaults defaults = provider == null || CUSTOM_PROVIDER.equals(provider)
            ? null
            : PROVIDER_DEFAULTS.get(provider);
        String host = defaults == null
            ? required(text(config, "smtp"), "smtp")
            : defaults.host();
        int port = defaults == null
            ? config.path("port").asInt(0)
            : defaults.port();
        if (port <= 0) {
            throw new IllegalArgumentException("Mail service port is required");
        }
        boolean ssl = "SSL".equalsIgnoreCase(text(config, "security"));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username(config));
        sender.setPassword(required(text(config, "password"), "password"));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        properties.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
        return sender;
    }

    private String fromAddress(JsonNode config, String templateSender) {
        if (templateSender != null) {
            return templateSender;
        }
        String senderEmail = text(config, "senderEmail");
        return senderEmail == null ? username(config) : senderEmail;
    }

    private String username(JsonNode config) {
        String senderEmail = text(config, "senderEmail");
        return senderEmail == null
            ? required(text(config, "username"), "username")
            : senderEmail;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Mail setting value must be valid JSON", ex);
        }
    }

    private String render(String content, Map<String, String> variables) {
        String rendered = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private String required(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Mail service field is required: " + field);
        }
        return value;
    }
}
