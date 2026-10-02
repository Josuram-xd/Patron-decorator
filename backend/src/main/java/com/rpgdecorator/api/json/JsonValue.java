package com.rpgdecorator.api.json;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A JSON document as a closed set of value types, so a switch over it needs no default. */
public sealed interface JsonValue {

    Null NULL = new Null();

    /** Members keep their insertion order. */
    record Obj(Map<String, JsonValue> members) implements JsonValue {
        public Obj {
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        /** The member with that name, or {@code null} if it is absent. */
        public JsonValue get(String name) {
            return members.get(name);
        }
    }

    record Arr(List<JsonValue> items) implements JsonValue {
        public Arr {
            items = List.copyOf(items);
        }
    }

    record Str(String value) implements JsonValue {
    }

    record Num(BigDecimal value) implements JsonValue {
        public boolean isInteger() {
            return value.stripTrailingZeros().scale() <= 0;
        }
    }

    record Bool(boolean value) implements JsonValue {
    }

    record Null() implements JsonValue {
    }

    static JsonValue string(String value) {
        return value == null ? NULL : new Str(value);
    }

    static JsonValue number(long value) {
        return new Num(BigDecimal.valueOf(value));
    }

    static JsonValue number(double value) {
        return new Num(BigDecimal.valueOf(value));
    }

    static JsonValue bool(boolean value) {
        return new Bool(value);
    }

    static JsonValue array(List<? extends JsonValue> items) {
        return new Arr(List.copyOf(items));
    }

    static JsonValue object(Map<String, ? extends JsonValue> members) {
        return new Obj(new LinkedHashMap<>(members));
    }
}
