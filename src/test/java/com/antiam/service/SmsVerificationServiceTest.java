package com.antiam.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antiam.sms.fixed.FixedCodeSmsConfig;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.api.utils.SmsRespUtils;
import org.dromara.sms4j.provider.service.AbstractSmsBlend;
import java.util.UUID;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SmsVerificationServiceTest {

    private String configId;

    @AfterEach
    void unregisterSmsBlend() {
        if (configId != null) {
            SmsFactory.unregister(configId);
        }
    }

    @Test
    void verifiesCodeReturnedByFixedCodeSms4jChannel() {
        SmsVerificationService service = serviceWithFixedCode("666666");

        service.sendCode(" 13800000000 ", "login");

        assertThatThrownBy(() -> service.verify("13800000000", "LOGIN", "123456"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid");

        service.verify("13800000000", "LOGIN", "666666");
    }

    @Test
    void consumesVerificationCodeAfterSuccessfulVerification() {
        SmsVerificationService service = serviceWithFixedCode("666666");

        service.sendCode("13800000000", "LOGIN");
        service.verify("13800000000", "LOGIN", "666666");

        assertThatThrownBy(() -> service.verify("13800000000", "LOGIN", "666666"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid");
    }

    @Test
    void failsWhenConfiguredSms4jChannelDoesNotExist() {
        SmsVerificationService service = new SmsVerificationService();
        ReflectionTestUtils.setField(service, "smsBlendId", "missing-" + UUID.randomUUID());
        ReflectionTestUtils.setField(service, "codeTtlSeconds", 300L);

        assertThatThrownBy(() -> service.sendCode("13800000000", "LOGIN"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SMS channel is not configured");
    }

    private SmsVerificationService serviceWithFixedCode(String code) {
        configId = "test-fixed-code-" + UUID.randomUUID();
        FixedCodeSmsConfig config = new FixedCodeSmsConfig();
        config.setConfigId(configId);
        config.setCode(code);
        SmsFactory.register(new TestFixedCodeSms(config));

        SmsVerificationService service = new SmsVerificationService();
        ReflectionTestUtils.setField(service, "smsBlendId", configId);
        ReflectionTestUtils.setField(service, "codeTtlSeconds", 300L);
        return service;
    }

    private static class TestFixedCodeSms extends AbstractSmsBlend<FixedCodeSmsConfig> {
        TestFixedCodeSms(FixedCodeSmsConfig config) {
            super(config, Runnable::run, null);
        }

        @Override
        public String getSupplier() {
            return "fixed-code";
        }

        @Override
        public SmsResponse sendMessage(String phone, String message) {
            return success();
        }

        @Override
        public SmsResponse sendMessage(String phone, java.util.LinkedHashMap<String, String> messages) {
            return success();
        }

        @Override
        public SmsResponse sendMessage(String phone, String templateId, java.util.LinkedHashMap<String, String> messages) {
            return success();
        }

        @Override
        public SmsResponse massTexting(java.util.List<String> phones, String message) {
            return success();
        }

        @Override
        public SmsResponse massTexting(java.util.List<String> phones, String templateId, java.util.LinkedHashMap<String, String> messages) {
            return success();
        }

        private SmsResponse success() {
            return SmsRespUtils.success(getConfig().getCode(), getConfigId());
        }
    }
}
