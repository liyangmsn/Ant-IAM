package com.antiam.service;

import com.antiam.dto.SettingDtos.IntegrationTestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailDeliveryService {

    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Za-z0-9_\\-.]+)}");

    private final SettingJsonSupport settings;

    public IntegrationTestResponse sendTest(String to, String templateKey) {
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Test recipient email is required");
        }
        JsonNode config = settings.json("message.mail.service");
        if (!config.path("enabled").asBoolean(false)) {
            throw new IllegalArgumentException("Mail service is disabled");
        }
        MailTemplate template = template(templateKey);
        send(config, to, template.subject(), template.content(), Map.of(
            "code", "666666",
            "time", "5",
            "username", "AntIAM",
            "password", "******",
            "expire_days", "7"
        ));
        return new IntegrationTestResponse(true, "测试邮件已提交发送");
    }

    private void send(JsonNode config, String to, String subject, String content, Map<String, String> variables) {
        try {
            JavaMailSenderImpl sender = mailSender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(fromAddress(config));
            helper.setSubject(render(subject, variables));
            helper.setText(render(content, variables), true);
            sender.send(message);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Mail delivery failed: " + ex.getMessage(), ex);
        }
    }

    private JavaMailSenderImpl mailSender(JsonNode config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(required(config, "smtp"));
        sender.setPort(config.path("port").asInt(465));
        sender.setUsername(requiredAny(config, "username", "senderEmail"));
        sender.setPassword(required(config, "password"));
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        String security = text(config, "security", "SSL");
        if ("SSL".equalsIgnoreCase(security)) {
            sender.getJavaMailProperties().put("mail.smtp.ssl.enable", "true");
        } else {
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
        }
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "10000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "10000");
        return sender;
    }

    private MailTemplate template(String templateKey) {
        String key = templateKey == null || templateKey.isBlank() ? "login_verify" : templateKey;
        JsonNode configured = settings.json("message.template." + key);
        String subject = text(configured, "subject", "AntIAM 测试邮件");
        String content = text(configured, "content", "您的动态码为：${code}，验证码${time}分钟内有效。");
        return new MailTemplate(subject, content);
    }

    private String render(String template, Map<String, String> variables) {
        Matcher matcher = TEMPLATE_VARIABLE.matcher(template == null ? "" : template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(variables.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String fromAddress(JsonNode config) {
        String senderEmail = requiredAny(config, "senderEmail", "username");
        String senderName = text(config, "senderName", text(config, "sender", ""));
        return senderName.isBlank() ? senderEmail : senderName + " <" + senderEmail + ">";
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Mail service field is required: " + field);
        }
        return value;
    }

    private String requiredAny(JsonNode node, String first, String second) {
        String value = text(node, first, "");
        if (!value.isBlank()) {
            return value;
        }
        return required(node, second);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    private record MailTemplate(String subject, String content) {
    }
}
