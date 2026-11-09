package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Die Siedlung hat eine neue Stufe erreicht oder ist abgestiegen.
 */
public record TierChanged(PolityId polity, SettlementTier from, SettlementTier to, Day occurredOn) implements PolityEvent {

    public TierChanged {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
