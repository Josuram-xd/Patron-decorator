package com.rpgdecorator.api.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rpgdecorator.api.json.JsonValue.Arr;
import com.rpgdecorator.api.json.JsonValue.Num;
import com.rpgdecorator.api.json.JsonValue.Obj;
import com.rpgdecorator.api.json.JsonValue.Str;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    private static int positionOf(String invalid) {
        return assertThrows(InvalidJsonException.class, () -> Json.parse(invalid)).position();
    }

    @Test
    void writesScalars() {
        assertEquals("null", Json.write(JsonValue.NULL));
        assertEquals("true", Json.write(JsonValue.bool(true)));
        assertEquals("42", Json.write(JsonValue.number(42)));
        assertEquals("-7", Json.write(JsonValue.number(-7)));
        assertEquals("0.8", Json.write(JsonValue.number(0.8)));
        assertEquals("1", Json.write(JsonValue.number(1.0)));
        assertEquals("\"hola\"", Json.write(JsonValue.string("hola")));
        assertEquals("null", Json.write(JsonValue.string(null)));
    }

    @Test
    void writesObjectsInInsertionOrderAndNestedArrays() {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        members.put("seq", JsonValue.number(41));
        members.put("type", JsonValue.string("DAMAGE"));
        members.put("critical", JsonValue.bool(true));
        members.put("layers", JsonValue.array(List.of(JsonValue.object(Map.of("position", JsonValue.number(0))))));
        members.put("extra", JsonValue.NULL);

        assertEquals("{\"seq\":41,\"type\":\"DAMAGE\",\"critical\":true,\"layers\":[{\"position\":0}],\"extra\":null}",
                Json.write(JsonValue.object(members)));
        assertEquals("{}", Json.write(JsonValue.object(Map.of())));
        assertEquals("[]", Json.write(JsonValue.array(List.of())));
    }

    @Test
    void escapesWhatJsonForbidsInStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", Json.write(JsonValue.string("a\"b\\c\nd\te\u0001")));
    }

    @Test
    void accentsAndEnyeSurviveARoundTrip() {
        JsonValue original = JsonValue.object(Map.of("name", JsonValue.string("Orco chamán, Dragón, señor ñandú")));

        String written = Json.write(original);

        assertTrue(written.contains("Orco chamán, Dragón, señor ñandú"));
        assertEquals(original, Json.parse(written));
    }

    @Test
    void parsesEscapesIncludingUnicode() {
        Str parsed = (Str) Json.parse("\"l\\u00ednea\\n\\ttab \\\"q\\\" \\\\ \\/ \\u00F1\"");

        assertEquals("línea\n\ttab \"q\" \\ / ñ", parsed.value());
    }

    @Test
    void parsesIntegersAndDecimals() {
        assertEquals(new BigDecimal("42"), ((Num) Json.parse("42")).value());
        assertEquals(new BigDecimal("-0.75"), ((Num) Json.parse("-0.75")).value());
        assertEquals(0, new BigDecimal("1200").compareTo(((Num) Json.parse("1.2e3")).value()));
        assertTrue(((Num) Json.parse("9007199254740993")).isInteger());
        assertEquals("9007199254740993", Json.write(Json.parse("9007199254740993")));
    }

    @Test
    void parsesNestedStructuresWithWhitespace() {
        Obj request = (Obj) Json.parse("""
                {
                  "heroClassId": "archer",
                  "itemIds": ["sword", "wind_boots"],
                  "nested": { "ok": true, "nothing": null, "list": [1, [2, 3], {}] }
                }
                """);

        assertEquals(new Str("archer"), request.get("heroClassId"));
        assertEquals(List.of(new Str("sword"), new Str("wind_boots")), ((Arr) request.get("itemIds")).items());
        Obj nested = (Obj) request.get("nested");
        assertEquals(JsonValue.bool(true), nested.get("ok"));
        assertEquals(JsonValue.NULL, nested.get("nothing"));
        assertEquals(3, ((Arr) nested.get("list")).items().size());
        assertNull(request.get("missing"));
    }

    @Test
    void roundTripKeepsTheDocument() {
        String text = "{\"id\":\"6f1c\",\"seed\":42,\"map\":[{\"level\":1,\"enemyId\":null}],\"rate\":0.5,\"ok\":false}";

        assertEquals(text, Json.write(Json.parse(text)));
    }

    @Test
    void invalidJsonReportsThePositionOfTheProblem() {
        assertEquals(0, positionOf(""));
        assertEquals(1, positionOf("{"));
        assertEquals(1, positionOf("{name: 1}"));
        assertEquals(7, positionOf("{\"a\":1,}"));
        assertEquals(5, positionOf("[1,2,"));
        assertEquals(3, positionOf("[1 2]"));
        assertEquals(4, positionOf("\"abc"));
        assertEquals(2, positionOf("\"\\x\""));
        assertEquals(5, positionOf("\"\\u12G4\""));
        assertEquals(0, positionOf("tru"));
        assertEquals(1, positionOf("-"));
        assertEquals(2, positionOf("1."));
        assertEquals(1, positionOf("01"));
        assertEquals(3, positionOf("{} x"));
        assertEquals(0, positionOf("'single'"));
    }

    @Test
    void nullTextIsRejected() {
        assertThrows(InvalidJsonException.class, () -> Json.parse(null));
    }
}
