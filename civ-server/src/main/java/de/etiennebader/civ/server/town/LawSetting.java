package de.etiennebader.civ.server.town;

import de.etiennebader.civ.server.command.Message;

import java.util.Objects;

/**
 * Eine Regelung mit ihrem geltenden Wert, wie sie ein Menü zeigt.
 *
 * @param value {@link Boolean} oder eine Zahl
 */
public record LawSetting(String id, Message name, Object value) {

    public LawSetting {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }

    public boolean isSwitch() {
        return value instanceof Boolean;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(value);
    }

    /** Der Zahlenwert; nur für Regelungen, die keine Schalter sind. */
    public long number() {
        return ((Number) value).longValue();
    }
}
