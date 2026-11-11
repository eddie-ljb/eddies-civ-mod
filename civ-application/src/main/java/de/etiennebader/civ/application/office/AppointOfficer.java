package de.etiennebader.civ.application.office;

import de.etiennebader.civ.application.polity.Citizens;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Der Bürgermeister ernennt einen Bürger zum Schatzmeister oder Bauamt, oder entlässt den Inhaber. Ernannte bleiben
 * im Amt, bis sie entlassen werden, austreten oder jemand anderes gewählt wird.
 */
public final class AppointOfficer {

    /** @param appointee leer zum Entlassen */
    public record Command(PolityId polity, ActorRef mayor, Office office, Optional<ActorRef> appointee, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(mayor, "mayor");
            Objects.requireNonNull(office, "office");
            Objects.requireNonNull(appointee, "appointee");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public AppointOfficer(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @return ob sich etwas geändert hat (Entlassen eines freien Amts ändert nichts)
     * @throws MayorNotAppointableException wenn das Amt das Bürgermeisteramt ist
     * @throws NotOfficeHolderException    wenn der Akteur nicht Bürgermeister ist
     */
    public boolean execute(Command command) {
        if (command.office() == Office.MAYOR) {
            throw new MayorNotAppointableException();
        }
        Polity polity = aggregates.polity(command.polity());
        if (!polity.holds(command.mayor(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.mayor(), Office.MAYOR);
        }
        boolean changed;
        if (command.appointee().isPresent()) {
            Citizens.require(polity, command.appointee().get());
            polity.fillOffice(command.office(), command.appointee().get(), Optional.empty(), command.day());
            changed = true;
        } else {
            changed = polity.vacateOffice(command.office(), command.day());
        }
        aggregates.commit(polity);
        return changed;
    }
}
