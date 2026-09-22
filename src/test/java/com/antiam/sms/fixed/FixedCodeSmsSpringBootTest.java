package com.antiam.sms.fixed;

import static org.assertj.core.api.Assertions.assertThat;

import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FixedCodeSmsSpringBootTest {

    @Test
    void registersConfiguredFixedCodeChannel() {
        assertThat(SmsFactory.getSmsBlend("fixed-code")).isNotNull();
    }
}
