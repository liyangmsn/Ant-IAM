package com.antiam.sms.fixed;

import org.dromara.sms4j.provider.config.BaseConfig;

public class FixedCodeSmsConfig extends BaseConfig {

    private String code = "666666";

    @Override
    public String getSupplier() {
        return FixedCodeSmsFactory.SUPPLIER;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
