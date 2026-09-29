package com.antiam.web;

import static com.antiam.dto.ScimDtos.ScimPatchOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCIM PATCH 辅助：规范化操作类型与路径，并从 add/replace/remove 的 value 中提取标量或多值属性。
 */
final class ScimPatchSupport {

    private static final Pattern VALUE_FILTER_PATH = Pattern.compile("^([a-zA-Z.]+)\\[\\s*value\\s+eq\\s+\"([^\"]*)\"\\s*]$");

    private ScimPatchSupport() {
    }

    static String op(ScimPatchOperation operation) {
        String op = operation.op().trim().toLowerCase(Locale.ROOT);
        if (!op.equals("add") && !op.equals("replace") && !op.equals("remove")) {
            throw new IllegalArgumentException("Unsupported SCIM PATCH op: " + operation.op());
        }
        return op;
    }

    /**
     * 去掉 schema URN 前缀并转为小写，例如 "urn:...:User:name.formatted" 转为 "name.formatted"。
     */
    static String path(String path, String schemaUrn) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String value = path.trim();
        if (value.regionMatches(true, 0, schemaUrn + ":", 0, schemaUrn.length() + 1)) {
            value = value.substring(schemaUrn.length() + 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * 解析 "members[value eq \"id\"]" 形式的路径，返回过滤的 value；不匹配时返回 null。
     */
    static String filteredValue(String normalizedPath, String attribute) {
        if (normalizedPath == null) {
            return null;
        }
        Matcher matcher = VALUE_FILTER_PATH.matcher(normalizedPath);
        return matcher.matches() && matcher.group(1).equals(attribute) ? matcher.group(2) : null;
    }

    /**
     * 从字符串、{"value": ...} 对象或多值数组（优先 primary）中提取单个文本值。
     */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return text(map.get("value"));
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            Object chosen = list.stream()
                .<Object>map(item -> item)
                .filter(item -> item instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("primary")))
                .findFirst()
                .orElse(list.getFirst());
            return text(chosen);
        }
        return String.valueOf(value);
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = text(value);
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("SCIM boolean value is invalid: " + text);
    }

    /**
     * 从 [{"value": "id"}] 或单个 {"value": "id"} 中提取成员 ID 列表。
     */
    static List<String> values(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(item -> {
                String text = text(item);
                if (text != null) {
                    result.add(text);
                }
            });
        } else {
            String text = text(value);
            if (text != null) {
                result.add(text);
            }
        }
        return result;
    }

    static Map<?, ?> attributes(ScimPatchOperation operation) {
        if (operation.value() instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("SCIM PATCH without path requires an object value");
    }
}
