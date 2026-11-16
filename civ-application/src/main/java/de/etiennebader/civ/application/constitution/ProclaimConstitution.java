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
 * Die Spitze einer Gemeinde oder eines Bundes ruft die Verfassung aus. Ab dann gelten die Verfassungsartikel als
 * geschützt: Sie ändern sich nur durch Abstimmung mit der Verfassungsmehrheit, auch wenn sonst verordnet wird.
 */
public final class ProclaimConstitution {

    public record Command(PolityId polity, ActorRef head, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(head, "head");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public ProclaimConstitution(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotOfficeHolderException wenn der Akteur nicht an der Spitze steht
     * @throws de.etiennebader.civ.domain.polity.ConstitutionAlreadyProclaimedException wenn schon ausgerufen
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        if (!polity.holds(command.head(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.head(), Office.MAYOR);
        }
        polity.proclaimConstitution(command.day());
        aggregates.commit(polity);
    }
}
