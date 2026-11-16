package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import de.etiennebader.civ.domain.law.StandardParameters;

import java.util.Objects;

/**
 * Ein Bürger wählt einen Kandidaten oder ändert seine Stimme. Ausgezählt wird erst am Ende der Wahl.
 */
public final class CastBallot {

    /**
     * @param atBallotBox ob die Stimme an einer Wahlurne abgegeben wird; schreibt das Recht die Urne vor
     *                    ({@link StandardParameters#BALLOT_BOX_REQUIRED}), zählt nur diese
     */
    public record Command(PolityId polity, ElectionId election, ActorRef citizen, ActorRef candidate, Day day,
                          boolean atBallotBox) {

        /** Stimme per Menü, Befehl oder Webseite. */
        public Command(PolityId polity, ElectionId election, ActorRef citizen, ActorRef candidate, Day day) {
            this(polity, election, citizen, candidate, day, false);
        }

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(election, "election");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public CastBallot(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Citizens.require(polity, command.citizen());
        if (!command.atBallotBox() && polity.laws().valueOf(StandardParameters.BALLOT_BOX_REQUIRED)) {
            throw new BallotBoxRequiredException();
        }
        polity.castBallot(command.election(), command.citizen(), command.candidate(), command.day());
        aggregates.commit(polity);
    }
}
