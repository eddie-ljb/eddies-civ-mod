package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ruft eine Wahl für ein Amt aus. Der Bürgermeister darf das jederzeit; jeder andere Bürger nur, wenn das Amt frei ist
 * oder seine Amtszeit abgelaufen ist. Die Wahl läuft {@link StandardParameters#ELECTION_DAYS} Tage.
 */
public final class CallElection {

    public record Command(PolityId polity, ActorRef citizen, Office office, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(citizen, "citizen");
            Objects.requireNonNull(office, "office");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final IdGenerator ids;

    public CallElection(Aggregates aggregates, IdGenerator ids) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /**
     * @return die neue Wahl
     * @throws de.etiennebader.civ.application.polity.NotACitizenException wenn der Akteur kein Bürger ist
     * @throws NotOfficeHolderException wenn das Amt besetzt ist und der Akteur nicht Bürgermeister
     * @throws de.etiennebader.civ.domain.polity.ElectionAlreadyOpenException wenn schon gewählt wird
     */
    public ElectionId execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        Citizens.require(polity, command.citizen());
        boolean open = polity.officeTerm(command.office()).map(term -> term.expiredOn(command.day())).orElse(true);
        if (!open && !polity.holds(command.citizen(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.citizen(), Office.MAYOR);
        }
        ElectionId election = call(polity, command.office(), ids, command.day());
        aggregates.commit(polity);
        return election;
    }

    static ElectionId call(Polity polity, Office office, IdGenerator ids, Day day) {
        ElectionId election = ElectionId.generate(ids);
        long days = polity.laws().valueOf(StandardParameters.ELECTION_DAYS);
        polity.callElection(election, office, day.plus(days), day);
        return election;
    }
}
