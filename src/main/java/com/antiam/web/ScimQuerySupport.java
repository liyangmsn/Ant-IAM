package com.antiam.web;

import static com.antiam.dto.ScimDtos.ScimListResponse;

import java.util.List;
import java.util.function.Predicate;

final class ScimQuerySupport {

    private static final List<String> LIST_SCHEMA = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    private ScimQuerySupport() {
    }

    /**
     * 对 SCIM 资源集合执行可选过滤和 1-based 分页，返回标准 ListResponse。
     */
    static <T> ScimListResponse<T> listResponse(
        List<T> resources,
        String filter,
        Integer startIndex,
        Integer count,
        Predicate<ScimFilter> filterSupport,
        java.util.function.BiPredicate<T, ScimFilter> matcher
    ) {
        ScimFilter parsed = ScimFilter.parse(filter);
        if (parsed != null && !filterSupport.test(parsed)) {
            throw new IllegalArgumentException("Unsupported SCIM filter attribute: " + parsed.attribute());
        }
        List<T> filtered = parsed == null ? resources : resources.stream()
            .filter(resource -> matcher.test(resource, parsed))
            .toList();
        int start = startIndex == null || startIndex < 1 ? 1 : startIndex;
        int limit = count == null || count < 0 ? filtered.size() : count;
        int from = Math.min(start - 1, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        List<T> page = filtered.subList(from, to);
        return new ScimListResponse<>(LIST_SCHEMA, filtered.size(), start, page.size(), page);
    }

    record ScimFilter(String attribute, String operator, String value) {
        static ScimFilter parse(String filter) {
            if (filter == null || filter.isBlank()) {
                return null;
            }
            String[] parts = filter.trim().split("\\s+", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("SCIM filter must use '<attribute> <op> <value>'");
            }
            return new ScimFilter(parts[0], parts[1], unquote(parts[2]));
        }

        /**
         * 执行轻量 SCIM 字符串匹配，支持 eq、co、sw 三类常用操作符。
         */
        boolean matches(String candidate) {
            if (candidate == null) {
                return false;
            }
            String left = candidate.toLowerCase();
            String right = value.toLowerCase();
            return switch (operator) {
                case "eq" -> left.equals(right);
                case "co" -> left.contains(right);
                case "sw" -> left.startsWith(right);
                default -> throw new IllegalArgumentException("Unsupported SCIM filter operator: " + operator);
            };
        }

        private static String unquote(String value) {
            String trimmed = value.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
            return trimmed;
        }
    }
}
