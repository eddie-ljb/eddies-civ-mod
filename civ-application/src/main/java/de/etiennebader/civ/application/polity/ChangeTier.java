package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Setzt die Siedlungsstufe einer Gemeinde, z. B. wenn sie genug Einwohner hat.
 */
public final class ChangeTier {

    public record Command(PolityId polity, SettlementTier tier, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public ChangeTier(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return ob sich die Stufe geändert hat
     */
    public boolean execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        boolean changed = polity.changeTier(command.tier(), command.day());
        aggregates.commit(polity);
        return changed;
    }
}
