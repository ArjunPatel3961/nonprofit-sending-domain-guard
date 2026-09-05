package org.nonprofit.mail;

import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    private Json() {}

    static String object(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(field.getKey())).append(':').append(quote(field.getValue()));
        }
        return json.append('}').toString();
    }

    static Map<String, Object> parseObject(String input) {
        Object value = new Parser(input).parseValue();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    static String string(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return value instanceof String text ? text : null;
    }

    static Map<String, Object> child(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.append('\"').toString();
    }

    private static final class Parser {
        private final String text;
        private int at;

        private Parser(String text) { this.text = text; }

        private Object parseValue() {
            whitespace();
            if (at >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return switch (text.charAt(at)) {
                case '{' -> parseMap();
                case '[' -> parseArray();
                case '\"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            whitespace();
            if (take('}')) return map;
            do {
                whitespace();
                String key = parseString();
                whitespace();
                require(':');
                map.put(key, parseValue());
                whitespace();
            } while (take(','));
            require('}');
            return map;
        }

        private Object parseArray() {
            java.util.List<Object> list = new java.util.ArrayList<>();
            at++;
            whitespace();
            if (take(']')) return list;
            do {
                list.add(parseValue());
                whitespace();
            } while (take(','));
            require(']');
            return list;
        }

        private String parseString() {
            require('\"');
            StringBuilder value = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '\"') return value.toString();
                if (c != '\\') {
                    value.append(c);
                    continue;
                }
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '\"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append((char) Integer.parseInt(text.substring(at, at += 4), 16));
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unclosed JSON string");
        }

        private Object parseNumber() {
            int start = at;
            while (at < text.length() && "-+0123456789.eE".indexOf(text.charAt(at)) >= 0) at++;
            String number = text.substring(start, at);
            try { return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.valueOf(number) : Long.valueOf(number); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid JSON value", error); }
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, at)) throw new IllegalArgumentException("Invalid JSON literal");
            at += expected.length();
            return value;
        }

        private void whitespace() { while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++; }
        private boolean take(char expected) { if (at < text.length() && text.charAt(at) == expected) { at++; return true; } return false; }
        private void require(char expected) { if (!take(expected)) throw new IllegalArgumentException("Expected " + expected); }
    }
}
