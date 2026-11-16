package de.etiennebader.civ.server.town;

import de.etiennebader.civ.server.command.Message;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Regelung mit ihrem geltenden Wert, wie sie ein Menü zeigt.
 *
 * @param value   geltender Wert, {@link Boolean} oder eine Zahl
 * @param federal Name des Bundes, dessen vorrangiges Recht hier gilt; leer, wenn das eigene Recht gilt
 * @param own     Wert nach eigenem Recht; weicht von {@code value} ab, wenn Bundesrecht vorgeht
 */
public record LawSetting(String id, Message name, Object value, Optional<String> federal, Object own) {

    /** Ohne Bundesrecht. */
    public LawSetting(String id, Message name, Object value) {
        this(id, name, value, Optional.empty(), value);
    }

    public LawSetting {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(federal, "federal");
        Objects.requireNonNull(own, "own");
    }

    /** Ob dies eine Auswahl ist, z. B. das Wahlverfahren; die Werte stehen in {@link #options()}. */
    public boolean isChoice() {
        return value instanceof String;
    }

    /** Die möglichen Werte einer Auswahl; leer bei allen anderen. */
    public java.util.List<String> options() {
        return new de.etiennebader.civ.domain.law.ParameterCatalog(
                de.etiennebader.civ.domain.law.StandardParameters.all()).find(id)
                .map(de.etiennebader.civ.domain.law.ParameterKey::options).orElse(java.util.List.of());
    }

    public boolean isSwitch() {
        return value instanceof Boolean;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(value);
    }

    /** Der Zahlenwert; nur für Regelungen, die keine Schalter sind. */
    public long number() {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
