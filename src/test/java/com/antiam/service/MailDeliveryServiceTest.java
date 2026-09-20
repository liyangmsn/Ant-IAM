package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antiam.domain.SettingValueType;
import com.antiam.domain.SystemSetting;
import com.antiam.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MailDeliveryServiceTest {

    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final MailDeliveryService service = new MailDeliveryService(
        settings,
        mock(AuditService.class),
        new ObjectMapper().findAndRegisterModules());

    @Test
    void rejectsDeliveryWhenMailServiceIsNotConfigured() {
        when(settings.findBySettingKey("message.mail.service")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.send("login_verify", "alice@example.com", Map.of("code", "123456")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mail service is not configured");
    }

    @Test
    void rejectsDeliveryWhenMailServiceIsDisabled() {
        when(settings.findBySettingKey("message.mail.service"))
            .thenReturn(Optional.of(setting("message.mail.service", """
                {"provider": "custom", "smtp": "smtp.example.com", "port": 465, "username": "noreply@example.com", "password": "secret", "enabled": false}
                """)));

        assertThatThrownBy(() -> service.send("login_verify", "alice@example.com", Map.of("code", "123456")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mail service is disabled");
    }

    @Test
    void rejectsUnknownTemplateKey() {
        assertThatThrownBy(() -> service.send("unknown_template", "alice@example.com", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown mail template");
    }

    @Test
    void rendersVariablesIntoCustomTemplateContent() {
        when(settings.findBySettingKey("message.mail.service"))
            .thenReturn(Optional.of(setting("message.mail.service", """
                {"provider": "netease", "senderEmail": "noreply@example.com", "password": "secret", "enabled": true}
                """)));
        when(settings.findBySettingKey("message.template.login_verify"))
            .thenReturn(Optional.of(setting("message.template.login_verify", """
                {"customEnabled": true, "sender": "系统账户 <noreply@example.com>", "subject": "验证码 ${code}", "content": "<p>${user_email} 的验证码是 ${code}</p>"}
                """)));

        assertThat(renderedSubject("login_verify")).isEqualTo("验证码 654321");
        assertThat(renderedContent("login_verify"))
            .isEqualTo("<p>alice@example.com 的验证码是 654321</p>");
    }

    @Test
    void fallsBackToBuiltInTemplateWhenCustomisationIsOff() {
        when(settings.findBySettingKey("message.mail.service"))
            .thenReturn(Optional.of(setting("message.mail.service", """
                {"provider": "netease", "senderEmail": "noreply@example.com", "password": "secret", "enabled": true}
                """)));
        when(settings.findBySettingKey("message.template.login_verify"))
            .thenReturn(Optional.of(setting("message.template.login_verify", """
                {"customEnabled": false, "sender": "", "subject": "", "content": ""}
                """)));

        assertThat(renderedSubject("login_verify")).isEqualTo("登录验证码");
        assertThat(renderedContent("login_verify")).contains("验证码为 654321");
    }

    private String renderedSubject(String templateKey) {
        return renderField(resolveTemplate(templateKey), "subject");
    }

    private String renderedContent(String templateKey) {
        return renderField(resolveTemplate(templateKey), "content");
    }

    private Object resolveTemplate(String templateKey) {
        return ReflectionTestUtils.invokeMethod(service, "resolveTemplate", templateKey, defaultTemplate(templateKey));
    }

    private String renderField(Object template, String accessor) {
        return ReflectionTestUtils.invokeMethod(service, "render",
            ReflectionTestUtils.invokeMethod(template, accessor),
            Map.of("code", "654321", "user_email", "alice@example.com"));
    }

    private Object defaultTemplate(String templateKey) {
        Object templates = ReflectionTestUtils.getField(service, "DEFAULT_TEMPLATES");
        return ((Map<?, ?>) templates).get(templateKey);
    }

    private SystemSetting setting(String key, String value) {
        return new SystemSetting(key, "message", SettingValueType.JSON, value, key, false);
    }
}
