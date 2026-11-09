package de.etiennebader.civ.server.web;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimales JSON: Schreiben verschachtelter Objekte und Lesen flacher Objekte, wie sie die Webseite schickt.
 */
public final class Json {

    private Json() {
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    /** Ein JSON-Array aus Elementen, die {@code toJson} jeweils als JSON-Text liefert. */
    public static <T> String array(Collection<T> items, Function<T, String> toJson) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (T item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(toJson.apply(item));
        }
        return out.append(']').toString();
    }

    public static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Baut ein JSON-Objekt in Einfügereihenfolge. */
    public static final class ObjectBuilder {

        private final StringBuilder out = new StringBuilder("{");
        private boolean first = true;

        public ObjectBuilder put(String key, String value) {
            return raw(key, value == null ? "null" : string(value));
        }

        public ObjectBuilder put(String key, long value) {
            return raw(key, Long.toString(value));
        }

        public ObjectBuilder put(String key, boolean value) {
            return raw(key, Boolean.toString(value));
        }

        /** Fügt bereits fertiges JSON ein, z. B. ein Array. */
        public ObjectBuilder raw(String key, String json) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(string(key)).append(':').append(json);
            return this;
        }

        public String build() {
            return out + "}";
        }
    }

    /**
     * Liest ein flaches JSON-Objekt mit Text-, Zahl- und Wahrheitswerten. Verschachtelte Werte werden abgelehnt.
     *
     * @throws IllegalArgumentException bei ungültigem JSON
     */
    public static Map<String, String> parseFlat(String json) {
        Parser parser = new Parser(json);
        Map<String, String> values = parser.object();
        parser.end();
        return values;
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        Map<String, String> object() {
            Map<String, String> values = new LinkedHashMap<>();
            skip();
            expect('{');
            skip();
            if (peek() == '}') {
                pos++;
                return values;
            }
            while (true) {
                skip();
                String key = stringValue();
                skip();
                expect(':');
                skip();
                values.put(key, value());
                skip();
                char next = next();
                if (next == '}') {
                    return values;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Erwartet , oder } an Stelle " + pos);
                }
            }
        }

        private String value() {
            char c = peek();
            if (c == '"') {
                return stringValue();
            }
            int start = pos;
            while (pos < text.length() && "-+.0123456789eEtruefalsn".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Ungültiger Wert an Stelle " + pos);
            }
            return text.substring(start, pos);
        }

        private String stringValue() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    char escaped = next();
                    switch (escaped) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new IllegalArgumentException("Unvollständiges \\u");
                            }
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> out.append(escaped);
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private void skip() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("Unerwartetes Ende");
            }
            return text.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalArgumentException("Erwartet " + c + " an Stelle " + (pos - 1));
            }
        }

        void end() {
            skip();
            if (pos != text.length()) {
                throw new IllegalArgumentException("Zusätzliche Zeichen nach dem Objekt");
            }
        }
    }
}
