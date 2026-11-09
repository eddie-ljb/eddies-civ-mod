package de.etiennebader.civ.server.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    @DisplayName("Flache Objekte mit Text, Zahl, Wahrheitswert und Escapes werden gelesen")
    void parsesFlatObjects() {
        assertEquals(Map.of("to", "Bob \"B\"", "n", "3", "yes", "true", "u", "ä"),
                Json.parseFlat(" {\"to\": \"Bob \\\"B\\\"\", \"n\": 3, \"yes\": true, \"u\": \"\\u00e4\"} "));
        assertEquals(Map.of(), Json.parseFlat("{}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseFlat("{\"a\": {\"b\": 1}}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseFlat("{\"a\": 1} x"));
    }

    @Test
    @DisplayName("Geschriebenes JSON maskiert Anführungszeichen und spitze Klammern")
    void escapesOutput() {
        assertEquals("{\"name\":\"\\u003cscript>\\\"\",\"n\":1,\"ok\":true}",
                Json.object().put("name", "<script>\"").put("n", 1).put("ok", true).build());
    }

    @Test
    @DisplayName("Itemnamen werden lesbar gemacht")
    void prettyNames() {
        assertEquals("Oak Log", WebApi.prettyName("minecraft:oak_log"));
    }
}
