package de.etiennebader.civ.application.law;

import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Das Recht, das in einer Gemeinde oder einem Bund tatsächlich gilt.
 *
 * <p>Regelt ein übergeordneter Bund etwas ausdrücklich und sieht seine Verfassung Vorrang des Bundesrechts vor
 * ({@link StandardParameters#FEDERAL_LAW_PREVAILS}), gilt sein Wert; der höchste solche Bund gewinnt. Verfahrensregeln
 * ({@link StandardParameters#constitutional()}) bleiben Sache jeder Ebene selbst.
 */
public final class EffectiveLaws implements LawView {

    private final PolityView own;
    private final List<? extends PolityView> ancestors;

    /**
     * @param ancestors übergeordnete Bünde, vom direkten Bund aufwärts
     */
    public EffectiveLaws(PolityView own, List<? extends PolityView> ancestors) {
        this.own = Objects.requireNonNull(own, "own");
        this.ancestors = List.copyOf(Objects.requireNonNull(ancestors, "ancestors"));
    }

    @Override
    public <T> T valueOf(ParameterKey<T> key) {
        return overriddenBy(key).flatMap(federation -> federation.laws().explicitValueOf(key))
                .orElseGet(() -> own.laws().valueOf(key));
    }

    /** Der Bund, dessen Recht hier statt des eigenen gilt; leer, wenn das eigene Recht gilt. */
    public Optional<PolityView> overriddenBy(ParameterKey<?> key) {
        Objects.requireNonNull(key, "key");
        if (StandardParameters.isConstitutional(key)) {
            return Optional.empty();
        }
        for (int index = ancestors.size() - 1; index >= 0; index--) {
            PolityView federation = ancestors.get(index);
            if (federation.laws().valueOf(StandardParameters.FEDERAL_LAW_PREVAILS)
                    && federation.laws().explicitValueOf(key).isPresent()) {
                return Optional.of(federation);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<EnactedStatute> inForce() {
        return own.laws().inForce();
    }
}
