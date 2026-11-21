package de.etiennebader.civ.domain.war;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein erklärter Krieg zwischen zwei Polities: von der Erklärung bis zu seinem Ende
 * (Gefecht oder ausgehandelter Frieden).
 */
public record War(WarId id, PolityId aggressor, PolityId defender, Day declaredOn, Optional<Day> endedOn,
                  Optional<PeaceTerms> outcome) {

    public War {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aggressor, "aggressor");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(declaredOn, "declaredOn");
        Objects.requireNonNull(endedOn, "endedOn");
        Objects.requireNonNull(outcome, "outcome");
        if (aggressor.equals(defender)) {
            throw new IllegalArgumentException("Angreifer und Verteidiger dürfen nicht dieselbe Polity sein");
        }
        if (outcome.isPresent() && endedOn.isEmpty()) {
            throw new IllegalArgumentException("Ein Kriegsergebnis setzt ein Enddatum voraus");
        }
    }

    static War declared(WarId id, PolityId aggressor, PolityId defender, Day day) {
        return new War(id, aggressor, defender, day, Optional.empty(), Optional.empty());
    }

    War ended(Day day, Optional<PeaceTerms> outcome) {
        return new War(id, aggressor, defender, declaredOn, Optional.of(day), outcome);
    }

    public boolean ongoing() {
        return endedOn.isEmpty();
    }

    public boolean involves(PolityId polity) {
        return aggressor.equals(polity) || defender.equals(polity);
    }

    /** Die jeweils andere Seite dieses Kriegs. */
    public PolityId opponentOf(PolityId polity) {
        if (aggressor.equals(polity)) {
            return defender;
        }
        if (defender.equals(polity)) {
            return aggressor;
        }
        throw new IllegalArgumentException("Polity ist nicht an diesem Krieg beteiligt: " + polity);
    }
}
