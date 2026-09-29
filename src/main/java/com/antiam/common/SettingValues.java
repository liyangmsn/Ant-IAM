package com.antiam.common;

import com.antiam.domain.SettingValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;

/**
 * 配置值校验：按声明的值类型检查内容，避免读取方解析失败。
 */
public final class SettingValues {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SettingValues() {
    }

    public static void validate(String settingKey, SettingValueType valueType, String value) {
        if (value == null || valueType == null || valueType == SettingValueType.STRING) {
            return;
        }
        String trimmed = value.trim();
        switch (valueType) {
            case BOOLEAN -> {
                if (!trimmed.equalsIgnoreCase("true") && !trimmed.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(settingKey + " must be true or false");
                }
            }
            case NUMBER -> {
                try {
                    new BigDecimal(trimmed);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(settingKey + " must be a number");
                }
            }
            case JSON -> {
                try {
                    JSON.readTree(trimmed);
                } catch (Exception ex) {
                    throw new IllegalArgumentException(settingKey + " must be valid JSON");
                }
            }
            default -> {
            }
        }
    }
}
