package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Bürger kandidiert bei einer laufenden Wahl, auf Wunsch mit Wahlprogramm. Dorfbewohner können wählen, aber
 * nicht kandidieren.
 */
public final class StandForElection {

    public record Command(PolityId polity, ElectionId election, ActorRef citizen, Platform platform, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(election, "election");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(platform, "platform");
            Objects.requireNonNull(day, "day");
        }

        /** Ohne Wahlprogramm. */
        public Command(PolityId polity, ElectionId election, ActorRef citizen, Day day) {
            this(polity, election, citizen, Platform.NONE, day);
        }
    }

    private final Aggregates aggregates;

    public StandForElection(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Citizens.require(polity, command.citizen());
        polity.standForElection(command.election(), command.citizen(), command.platform(), command.day());
        aggregates.commit(polity);
    }
}
