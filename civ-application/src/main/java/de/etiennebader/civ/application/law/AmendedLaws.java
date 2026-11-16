package de.etiennebader.civ.application.law;

import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Das Recht, wie es nach Annahme eines Antrags gälte; zum Abwägen, z. B. für Abgeordnete. Bei einer Aufhebung gelten
 * für die Regelungen des Paragraphen wieder die übrigen Paragraphen oder die Standardwerte.
 */
public final class AmendedLaws implements LawView {

    private final LawView base;
    private final Map<ParameterKey<?>, Object> overrides;

    private AmendedLaws(LawView base, Map<ParameterKey<?>, Object> overrides) {
        this.base = base;
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * @param base   das jetzt geltende Recht
     * @param polity die Polity, über deren Antrag entschieden wird
     */
    public static LawView of(LawView base, PolityView polity, Motion motion) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(motion, "motion");
        Map<ParameterKey<?>, Object> overrides = new HashMap<>();
        if (motion instanceof Motion.Repeal repeal) {
            List<EnactedStatute> remaining = polity.laws().inForce().stream()
                    .filter(enacted -> !enacted.statute().id().equals(repeal.statute()))
                    .toList();
            polity.laws().inForce().stream()
                    .filter(enacted -> enacted.statute().id().equals(repeal.statute()))
                    .flatMap(enacted -> enacted.statute().values().stream())
                    .forEach(value -> overrides.put(value.key(), lastValue(remaining, value.key())));
        }
        for (ParameterValue<?> value : motion.values()) {
            overrides.put(value.key(), value.value());
        }
        return new AmendedLaws(base, overrides);
    }

    private static Object lastValue(List<EnactedStatute> statutes, ParameterKey<?> key) {
        for (int index = statutes.size() - 1; index >= 0; index--) {
            Object value = statutes.get(index).statute().valueOf(key).orElse(null);
            if (value != null) {
                return value;
            }
        }
        return key.defaultValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T valueOf(ParameterKey<T> key) {
        Object value = overrides.get(key);
        return value != null ? (T) value : base.valueOf(key);
    }

    @Override
    public List<EnactedStatute> inForce() {
        return base.inForce();
    }
}
