package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Der Vorsitz ändert Richtung oder Wappen seiner Partei.
 */
public final class ChangePartyProfile {

    public record Command(PolityId party, ActorRef head, PartyProfile profile, Day day) {

        public Command {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(head, "head");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public ChangePartyProfile(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotAPartyException       wenn die Polity keine Partei ist
     * @throws NotOfficeHolderException wenn der Akteur nicht den Vorsitz hat
     */
    public void execute(Command command) {
        Polity party = aggregates.polity(command.party());
        if (party.kind() != PolityKind.PARTY) {
            throw new NotAPartyException(party.name());
        }
        if (!party.holds(command.head(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.head(), Office.MAYOR);
        }
        party.changePartyProfile(command.profile(), command.day());
        aggregates.commit(party);
    }
}
