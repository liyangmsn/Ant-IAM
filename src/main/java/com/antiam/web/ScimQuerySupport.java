package com.antiam.web;

import static com.antiam.dto.ScimDtos.ScimListResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;
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
        BiPredicate<T, ScimFilter> matcher
    ) {
        FilterNode parsed = FilterParser.parse(filter);
        if (parsed != null) {
            List<ScimFilter> leaves = new ArrayList<>();
            parsed.collect(leaves);
            leaves.stream()
                .filter(leaf -> !filterSupport.test(leaf))
                .findFirst()
                .ifPresent(leaf -> {
                    throw new IllegalArgumentException("Unsupported SCIM filter attribute: " + leaf.attribute());
                });
        }
        List<T> filtered = parsed == null ? resources : resources.stream()
            .filter(resource -> parsed.test(leaf -> matcher.test(resource, leaf)))
            .toList();
        int start = startIndex == null || startIndex < 1 ? 1 : startIndex;
        int limit = count == null || count < 0 ? filtered.size() : count;
        int from = Math.min(start - 1, filtered.size());
        int to = Math.min(from + limit, filtered.size());
        List<T> page = filtered.subList(from, to);
        return new ScimListResponse<>(LIST_SCHEMA, filtered.size(), start, page.size(), page);
    }

    /**
     * SCIM 过滤表达式语法树：叶子为单个属性比较，内部节点为 and / or / not。
     */
    sealed interface FilterNode permits ScimFilter, And, Or, Not {
        boolean test(Predicate<ScimFilter> leafMatcher);

        void collect(List<ScimFilter> leaves);
    }

    record And(FilterNode left, FilterNode right) implements FilterNode {
        public boolean test(Predicate<ScimFilter> leafMatcher) {
            return left.test(leafMatcher) && right.test(leafMatcher);
        }

        public void collect(List<ScimFilter> leaves) {
            left.collect(leaves);
            right.collect(leaves);
        }
    }

    record Or(FilterNode left, FilterNode right) implements FilterNode {
        public boolean test(Predicate<ScimFilter> leafMatcher) {
            return left.test(leafMatcher) || right.test(leafMatcher);
        }

        public void collect(List<ScimFilter> leaves) {
            left.collect(leaves);
            right.collect(leaves);
        }
    }

    record Not(FilterNode inner) implements FilterNode {
        public boolean test(Predicate<ScimFilter> leafMatcher) {
            return !inner.test(leafMatcher);
        }

        public void collect(List<ScimFilter> leaves) {
            inner.collect(leaves);
        }
    }

    record ScimFilter(String attribute, String operator, String value) implements FilterNode {

        private static final Set<String> OPERATORS = Set.of("eq", "ne", "co", "sw", "ew", "pr");

        public boolean test(Predicate<ScimFilter> leafMatcher) {
            return leafMatcher.test(this);
        }

        public void collect(List<ScimFilter> leaves) {
            leaves.add(this);
        }

        /**
         * 执行大小写不敏感的 SCIM 字符串匹配，支持 eq、ne、co、sw、ew、pr。
         */
        boolean matches(String candidate) {
            if ("pr".equals(operator)) {
                return candidate != null && !candidate.isBlank();
            }
            if (candidate == null) {
                return "ne".equals(operator) && value != null;
            }
            String left = candidate.toLowerCase(Locale.ROOT);
            String right = value == null ? "" : value.toLowerCase(Locale.ROOT);
            return switch (operator) {
                case "eq" -> left.equals(right);
                case "ne" -> !left.equals(right);
                case "co" -> left.contains(right);
                case "sw" -> left.startsWith(right);
                case "ew" -> left.endsWith(right);
                default -> throw new IllegalArgumentException("Unsupported SCIM filter operator: " + operator);
            };
        }
    }

    /**
     * 递归下降解析器：or 优先级最低，其次 and，not 仅作用于括号表达式。
     */
    static final class FilterParser {

        private final List<Token> tokens;
        private int position;

        private FilterParser(List<Token> tokens) {
            this.tokens = tokens;
        }

        static FilterNode parse(String filter) {
            if (filter == null || filter.isBlank()) {
                return null;
            }
            FilterParser parser = new FilterParser(tokenize(filter));
            FilterNode node = parser.parseOr();
            if (parser.position < parser.tokens.size()) {
                throw new IllegalArgumentException("Unexpected SCIM filter token: " + parser.tokens.get(parser.position).text());
            }
            return node;
        }

        private FilterNode parseOr() {
            FilterNode left = parseAnd();
            while (keyword("or")) {
                position++;
                left = new Or(left, parseAnd());
            }
            return left;
        }

        private FilterNode parseAnd() {
            FilterNode left = parseUnary();
            while (keyword("and")) {
                position++;
                left = new And(left, parseUnary());
            }
            return left;
        }

        private FilterNode parseUnary() {
            if (keyword("not")) {
                position++;
                if (!symbol("(")) {
                    throw new IllegalArgumentException("SCIM filter 'not' must be followed by '('");
                }
                return new Not(parseGroup());
            }
            if (symbol("(")) {
                return parseGroup();
            }
            return parseComparison();
        }

        private FilterNode parseGroup() {
            position++;
            FilterNode inner = parseOr();
            if (!symbol(")")) {
                throw new IllegalArgumentException("SCIM filter is missing ')'");
            }
            position++;
            return inner;
        }

        private FilterNode parseComparison() {
            Token attribute = next("attribute");
            if (attribute.quoted() || attribute.symbol()) {
                throw new IllegalArgumentException("SCIM filter must use '<attribute> <op> <value>'");
            }
            String operator = next("operator").text().toLowerCase(Locale.ROOT);
            if (!ScimFilter.OPERATORS.contains(operator)) {
                throw new IllegalArgumentException("Unsupported SCIM filter operator: " + operator);
            }
            if ("pr".equals(operator)) {
                return new ScimFilter(attribute.text(), operator, null);
            }
            Token value = next("value");
            if (value.symbol()) {
                throw new IllegalArgumentException("SCIM filter must use '<attribute> <op> <value>'");
            }
            return new ScimFilter(attribute.text(), operator, value.text());
        }

        private Token next(String expected) {
            if (position >= tokens.size()) {
                throw new IllegalArgumentException("SCIM filter must use '<attribute> <op> <value>', missing " + expected);
            }
            return tokens.get(position++);
        }

        private boolean keyword(String word) {
            if (position >= tokens.size()) {
                return false;
            }
            Token token = tokens.get(position);
            return !token.quoted() && !token.symbol() && token.text().equalsIgnoreCase(word);
        }

        private boolean symbol(String value) {
            return position < tokens.size() && tokens.get(position).symbol() && tokens.get(position).text().equals(value);
        }

        private static List<Token> tokenize(String filter) {
            List<Token> tokens = new ArrayList<>();
            int i = 0;
            while (i < filter.length()) {
                char c = filter.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                } else if (c == '(' || c == ')') {
                    tokens.add(new Token(String.valueOf(c), false, true));
                    i++;
                } else if (c == '"') {
                    StringBuilder value = new StringBuilder();
                    i++;
                    boolean closed = false;
                    while (i < filter.length()) {
                        char ch = filter.charAt(i++);
                        if (ch == '\\' && i < filter.length()) {
                            value.append(filter.charAt(i++));
                        } else if (ch == '"') {
                            closed = true;
                            break;
                        } else {
                            value.append(ch);
                        }
                    }
                    if (!closed) {
                        throw new IllegalArgumentException("SCIM filter has an unterminated string");
                    }
                    tokens.add(new Token(value.toString(), true, false));
                } else {
                    int start = i;
                    while (i < filter.length() && !Character.isWhitespace(filter.charAt(i))
                        && filter.charAt(i) != '(' && filter.charAt(i) != ')') {
                        i++;
                    }
                    tokens.add(new Token(filter.substring(start, i), false, false));
                }
            }
            return tokens;
        }
    }

    private record Token(String text, boolean quoted, boolean symbol) {
    }
}
