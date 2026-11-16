package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.TermLimitException;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        requireTermLimit(polity, command);
        polity.standForElection(command.election(), command.citizen(), command.platform(), command.day());
        aggregates.commit(polity);
    }

    /**
     * Amtszeitbegrenzung ({@link StandardParameters#TERM_LIMIT}): Wer ein einzelnes Amt so oft in Folge gewonnen hat,
     * wie erlaubt, darf nicht erneut kandidieren.
     */
    private static void requireTermLimit(Polity polity, Command command) {
        long limit = polity.laws().valueOf(StandardParameters.TERM_LIMIT);
        if (limit <= 0) {
            return;
        }
        Optional<Office> office = polity.elections().stream()
                .filter(election -> election.id().equals(command.election())).map(Election::office).findFirst();
        if (office.isEmpty() || !office.get().single()) {
            return;
        }
        List<ElectionResult> results = polity.results().stream()
                .filter(result -> result.office() == office.get() && !result.winners().isEmpty()).toList();
        long streak = 0;
        for (int index = results.size() - 1; index >= 0; index--) {
            if (!results.get(index).winners().contains(command.citizen())) {
                break;
            }
            streak++;
        }
        if (streak >= limit) {
            throw new TermLimitException(command.citizen(), limit);
        }
    }
}
