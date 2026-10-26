package de.etiennebader.civ.persistence.json;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.persistence.ActorCodec;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Wandelt Domain Events (Records) per Reflexion in kompaktes JSON, ohne externe Bibliothek.
 *
 * <p>Regeln: Records werden zu Objekten mit ihren Komponenten; Records mit genau einer Komponente
 * (IDs, {@code Day}, {@code CurrencyId}, Namen) werden auf diesen Wert reduziert; Akteure werden zu
 * {@code {"kind":"PLAYER","id":"…"}}; Enums zu ihrem Namen; Collections zu Arrays.
 * Neue Events brauchen deshalb keinen eigenen Code.
 */
public final class EventJson {

    /** Komponenten und Zugriffsmethoden je Record-Klasse, einmal ermittelt statt bei jedem Event. */
    private static final ClassValue<List<Component>> COMPONENTS = new ClassValue<>() {
        @Override
        protected List<Component> computeValue(Class<?> type) {
            return Arrays.stream(type.getRecordComponents()).map(Component::of).toList();
        }
    };

    private EventJson() {
    }

    public static String toJson(Object value) {
        StringBuilder json = new StringBuilder();
        write(json, value);
        return json.toString();
    }

    private static void write(StringBuilder json, Object value) {
        switch (value) {
            case null -> json.append("null");
            case ActorRef actor -> json.append("{\"kind\":\"").append(ActorCodec.kindOf(actor))
                    .append("\",\"id\":\"").append(actor.uuid()).append("\"}");
            case Number number -> json.append(number);
            case Boolean bool -> json.append(bool);
            case Enum<?> constant -> string(json, constant.name());
            case CharSequence text -> string(json, text.toString());
            case UUID uuid -> string(json, uuid.toString());
            case Collection<?> items -> array(json, items);
            case Record record -> record(json, record);
            default -> string(json, value.toString());
        }
    }

    private static void record(StringBuilder json, Record record) {
        List<Component> components = COMPONENTS.get(record.getClass());
        if (components.size() == 1) {
            write(json, components.getFirst().valueOf(record));
            return;
        }
        json.append('{');
        for (int index = 0; index < components.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            Component component = components.get(index);
            json.append(component.quotedName()).append(':');
            write(json, component.valueOf(record));
        }
        json.append('}');
    }

    private static void array(StringBuilder json, Collection<?> items) {
        json.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                json.append(',');
            }
            write(json, item);
            first = false;
        }
        json.append(']');
    }

    /** Eine Record-Komponente: ihr Name als fertiges JSON und ein schneller Zugriff auf den Wert. */
    private record Component(String quotedName, MethodHandle accessor) {

        static Component of(RecordComponent component) {
            try {
                MethodHandle accessor = MethodHandles.publicLookup().unreflect(component.getAccessor())
                        .asType(MethodType.methodType(Object.class, Record.class));
                StringBuilder name = new StringBuilder();
                string(name, component.getName());
                return new Component(name.toString(), accessor);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Komponente nicht lesbar: " + component, e);
            }
        }

        Object valueOf(Record record) {
            try {
                return accessor.invokeExact(record);
            } catch (Throwable e) {
                throw new IllegalStateException("Komponente nicht lesbar: " + quotedName, e);
            }
        }
    }

    private static void string(StringBuilder json, String text) {
        json.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
