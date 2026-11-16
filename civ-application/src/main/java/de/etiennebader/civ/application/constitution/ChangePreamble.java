package de.etiennebader.civ.application.constitution;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Die Spitze schreibt die Präambel des Verfassungsentwurfs. Nach dem Ausrufen steht sie fest.
 */
public final class ChangePreamble {

    public record Command(PolityId polity, ActorRef head, String preamble, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(head, "head");
            Objects.requireNonNull(preamble, "preamble");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public ChangePreamble(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotOfficeHolderException wenn der Akteur nicht an der Spitze steht
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        if (!polity.holds(command.head(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.head(), Office.MAYOR);
        }
        polity.changePreamble(command.preamble(), command.day());
        aggregates.commit(polity);
    }
}
