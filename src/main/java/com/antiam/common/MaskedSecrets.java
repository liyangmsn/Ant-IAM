package com.antiam.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 脱敏占位符处理：管理端读取到的敏感值是 "******"，原样提交回来时需要还原为数据库中的真实值，避免覆盖密钥。
 */
public final class MaskedSecrets {

    public static final String MASK = "******";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SENSITIVE_JSON_FIELDS =
        "(?i)(\"(?:appSecret|corpSecret|secret|secretKey|clientSecret|password|encryptKey|verificationToken|token)\"\\s*:\\s*\")([^\"]+)(\")";

    private MaskedSecrets() {
    }

    /**
     * 将 JSON 文本中常见敏感字段的值替换为占位符，用于管理端回显配置。
     */
    public static String maskJsonFields(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        return json.replaceAll(SENSITIVE_JSON_FIELDS, "$1" + MASK + "$3");
    }

    /**
     * 非空值统一回显为占位符。
     */
    public static String mask(String value) {
        return value == null || value.isBlank() ? value : MASK;
    }

    /**
     * 整体值为占位符时沿用原值；两者均为 JSON 对象时逐字段还原占位符。
     */
    public static String restore(String incoming, String original) {
        if (incoming == null || original == null) {
            return incoming;
        }
        if (MASK.equals(incoming.trim())) {
            return original;
        }
        if (!incoming.contains(MASK)) {
            return incoming;
        }
        try {
            JsonNode incomingNode = JSON.readTree(incoming);
            JsonNode originalNode = JSON.readTree(original);
            if (incomingNode instanceof ObjectNode incomingObject && originalNode instanceof ObjectNode originalObject) {
                restoreFields(incomingObject, originalObject);
                return JSON.writeValueAsString(incomingObject);
            }
        } catch (Exception ignored) {
            return incoming;
        }
        return incoming;
    }

    private static void restoreFields(ObjectNode incoming, ObjectNode original) {
        incoming.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            JsonNode originalValue = original.get(entry.getKey());
            if (value instanceof ObjectNode valueObject && originalValue instanceof ObjectNode originalObject) {
                restoreFields(valueObject, originalObject);
                return;
            }
            if (value.isTextual() && MASK.equals(value.asText()) && originalValue != null) {
                incoming.set(entry.getKey(), originalValue);
            }
        });
    }
}
