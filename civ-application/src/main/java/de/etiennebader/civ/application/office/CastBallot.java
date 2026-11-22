package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import de.etiennebader.civ.domain.law.StandardParameters;

import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.PolityView;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

    private final Function<PolityView, Function<ActorRef, Optional<PolityId>>> districtOf;

    public CastBallot(Aggregates aggregates) {
        this(aggregates, polity -> actor -> Optional.empty());
    }

    /**
     * @param districtOf der Wahlbezirk eines Wählers je Polity; bei Wahlkreiswahlen darf er nur Kandidaten seines
     *                   eigenen Wahlkreises wählen (#47)
     */
    public CastBallot(Aggregates aggregates, Function<PolityView, Function<ActorRef, Optional<PolityId>>> districtOf) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.districtOf = Objects.requireNonNull(districtOf, "districtOf");
    }

    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Citizens.require(polity, command.citizen());
        if (!command.atBallotBox() && polity.laws().valueOf(StandardParameters.BALLOT_BOX_REQUIRED)) {
            throw new BallotBoxRequiredException();
        }
        requireOwnDistrict(polity, command);
        polity.castBallot(command.election(), command.citizen(), command.candidate(), command.day());
        aggregates.commit(polity);
    }

    /** Wahlkreiswahl: Der Kandidat muss im Wahlkreis des Wählers antreten. */
    private void requireOwnDistrict(Polity polity, Command command) {
        Election election = polity.elections().stream().filter(found -> found.id().equals(command.election()))
                .findFirst().orElse(null);
        if (election == null || !DistrictVoting.applies(polity, election, aggregates.civic())) {
            return;
        }
        Optional<PolityId> candidateDistrict = election.districtOf(command.candidate());
        Optional<PolityId> voterDistrict = districtOf.apply(polity).apply(command.citizen());
        if (candidateDistrict.isPresent() && !candidateDistrict.equals(voterDistrict)) {
            throw new VoteOutsideDistrictException(command.candidate());
        }
    }
}
