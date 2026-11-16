package de.etiennebader.civ.application.federation;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Gemeinde oder ein Bund tritt aus seinem Bund aus; ihre Leute verlassen ihn und alle Bünde darüber.
 */
public final class LeaveFederation {

    public record Command(PolityId constituent, Day day) {

        public Command {
            Objects.requireNonNull(constituent, "constituent");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public LeaveFederation(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return der verlassene Bund
     * @throws NotInFederationException  wenn die Polity keinem Bund angehört
     * @throws LastConstituentException  wenn sie das letzte Glied ist; dann muss der Bund aufgelöst werden
     */
    public PolityId execute(Command command) {
        Polity constituent = aggregates.polity(command.constituent());
        Polity parent = Federations.parentOf(aggregates.repositories().polities().findAll(), constituent.id())
                .orElseThrow(() -> new NotInFederationException(constituent.name()));
        if (parent.constituents().size() <= 1) {
            throw new LastConstituentException(parent.name());
        }
        Federations.detach(aggregates, constituent, command.day());
        return parent.id();
    }
}
