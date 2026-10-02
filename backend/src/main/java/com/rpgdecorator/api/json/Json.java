package com.rpgdecorator.api.json;

import com.rpgdecorator.api.json.JsonValue.Arr;
import com.rpgdecorator.api.json.JsonValue.Bool;
import com.rpgdecorator.api.json.JsonValue.Null;
import com.rpgdecorator.api.json.JsonValue.Num;
import com.rpgdecorator.api.json.JsonValue.Obj;
import com.rpgdecorator.api.json.JsonValue.Str;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal hand-written JSON writer and recursive-descent parser (RNF-01: no libraries). */
public final class Json {

    private Json() {
    }

    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    public static JsonValue parse(String text) {
        if (text == null) {
            throw new InvalidJsonException("Nothing to parse", 0);
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        JsonValue value = parser.value();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected content after the JSON value");
        }
        return value;
    }

    private static void write(JsonValue value, StringBuilder out) {
        switch (value) {
            case Null ignored -> out.append("null");
            case Bool bool -> out.append(bool.value());
            case Num num -> out.append(num.isInteger()
                    ? num.value().toBigInteger().toString()
                    : num.value().stripTrailingZeros().toPlainString());
            case Str str -> writeString(str.value(), out);
            case Arr arr -> {
                out.append('[');
                String separator = "";
                for (JsonValue item : arr.items()) {
                    out.append(separator);
                    write(item, out);
                    separator = ",";
                }
                out.append(']');
            }
            case Obj obj -> {
                out.append('{');
                String separator = "";
                for (Map.Entry<String, JsonValue> member : obj.members().entrySet()) {
                    out.append(separator);
                    writeString(member.getKey(), out);
                    out.append(':');
                    write(member.getValue(), out);
                    separator = ",";
                }
                out.append('}');
            }
        }
    }

    // Accented letters and ñ go out as they are: the response is UTF-8. Only what JSON forbids is escaped.
    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        InvalidJsonException error(String message) {
            return new InvalidJsonException(message, pos);
        }

        void skipWhitespace() {
            while (!atEnd() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
        }

        JsonValue value() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> new Str(string());
                case 't' -> literal("true", new Bool(true));
                case 'f' -> literal("false", new Bool(false));
                case 'n' -> literal("null", JsonValue.NULL);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private JsonValue literal(String word, JsonValue value) {
            if (!text.startsWith(word, pos)) {
                throw error("Expected '" + word + "'");
            }
            pos += word.length();
            return value;
        }

        private JsonValue object() {
            Map<String, JsonValue> members = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return new Obj(members);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected a member name");
                }
                String name = string();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                members.put(name, value());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == '}') {
                    return new Obj(members);
                }
                if (next != ',') {
                    pos--;
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private JsonValue array() {
            List<JsonValue> items = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return new Arr(items);
            }
            while (true) {
                skipWhitespace();
                items.add(value());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == ']') {
                    return new Arr(items);
                }
                if (next != ',') {
                    pos--;
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            pos++;
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos);
                if (c == '"') {
                    pos++;
                    return out.toString();
                }
                if (c < 0x20) {
                    throw error("Control character inside a string");
                }
                if (c != '\\') {
                    out.append(c);
                    pos++;
                    continue;
                }
                pos++;
                if (atEnd()) {
                    throw error("Unterminated escape");
                }
                char escaped = text.charAt(pos);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> out.append(unicode());
                    default -> throw error("Invalid escape '\\" + escaped + "'");
                }
                pos++;
            }
        }

        // Leaves pos on the last hex digit; the caller advances past it.
        private char unicode() {
            if (pos + 4 >= text.length()) {
                throw error("Incomplete \\u escape");
            }
            int code = 0;
            for (int i = 1; i <= 4; i++) {
                int digit = Character.digit(text.charAt(pos + i), 16);
                if (digit < 0) {
                    pos += i;
                    throw error("Invalid hex digit in \\u escape");
                }
                code = code * 16 + digit;
            }
            pos += 4;
            return (char) code;
        }

        private JsonValue number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            if (peek() == '0') {
                pos++;
            } else {
                digits();
            }
            if (!atEnd() && text.charAt(pos) == '.') {
                pos++;
                digits();
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                digits();
            }
            return new Num(new BigDecimal(text.substring(start, pos)));
        }

        private void digits() {
            int start = pos;
            while (!atEnd() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
            if (pos == start) {
                throw error("Expected a digit");
            }
        }

        private char peek() {
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            return text.charAt(pos);
        }

        private void expect(char expected) {
            if (peek() != expected) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }
    }
}
