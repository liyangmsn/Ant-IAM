package com.antiam.sms.fixed;

import org.dromara.sms4j.provider.factory.AbstractProviderFactory;
import org.springframework.stereotype.Component;

@Component
public class FixedCodeSmsFactory extends AbstractProviderFactory<FixedCodeSmsImpl, FixedCodeSmsConfig> {

    public static final String SUPPLIER = "fixed-code";

    @Override
    public FixedCodeSmsImpl createSms(FixedCodeSmsConfig config) {
        return new FixedCodeSmsImpl(config);
    }

    @Override
    public String getSupplier() {
        return SUPPLIER;
    }
}
