package com.antiam.sms.fixed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import org.dromara.sms4j.comm.delayedTime.DelayedTime;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.api.utils.SmsRespUtils;
import org.dromara.sms4j.provider.service.AbstractSmsBlend;

public class FixedCodeSmsImpl extends AbstractSmsBlend<FixedCodeSmsConfig> {

    public FixedCodeSmsImpl(FixedCodeSmsConfig config) {
        super(config);
    }

    FixedCodeSmsImpl(FixedCodeSmsConfig config, Executor executor, DelayedTime delayedTime) {
        super(config, executor, delayedTime);
    }

    @Override
    public String getSupplier() {
        return FixedCodeSmsFactory.SUPPLIER;
    }

    @Override
    public SmsResponse sendMessage(String phone, String message) {
        return success();
    }

    @Override
    public SmsResponse sendMessage(String phone, LinkedHashMap<String, String> messages) {
        return success();
    }

    @Override
    public SmsResponse sendMessage(String phone, String templateId, LinkedHashMap<String, String> messages) {
        return success();
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String message) {
        return success();
    }

    @Override
    public SmsResponse massTexting(List<String> phones, String templateId, LinkedHashMap<String, String> messages) {
        return success();
    }

    private SmsResponse success() {
        return SmsRespUtils.success(getConfig().getCode(), getConfigId());
    }
}
