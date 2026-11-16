package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Wahlversprechen: Freitext und die Regelungen, die das Gesetzbuch darin erkennt, z. B. „Ich senke die
 * Umsatzsteuer auf 5 %.“
 *
 * @param values erkannte Regelungen; leer, wenn das Versprechen nichts Messbares enthält
 */
public record Promise(ActorRef candidate, Office office, String text, List<ParameterValue<?>> values, Day madeOn) {

    public static final int MAX_TEXT = 200;

    public Promise {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(text, "text");
        text = text.strip();
        if (text.isEmpty() || text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Versprechen muss 1 bis " + MAX_TEXT + " Zeichen haben");
        }
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        Objects.requireNonNull(madeOn, "madeOn");
    }

    /**
     * Ob das Recht das Versprechen jetzt erfüllt: Anteil der erkannten Regelungen, die so gelten, wie versprochen.
     *
     * @return leer, wenn nichts Messbares versprochen wurde
     */
    public Optional<Double> keptShare(LawView laws) {
        Objects.requireNonNull(laws, "laws");
        if (values.isEmpty()) {
            return Optional.empty();
        }
        long kept = values.stream().filter(value -> Objects.equals(laws.valueOf(value.key()), value.value())).count();
        return Optional.of(kept / (double) values.size());
    }
}
