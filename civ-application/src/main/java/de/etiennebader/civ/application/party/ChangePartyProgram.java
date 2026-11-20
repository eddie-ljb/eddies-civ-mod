package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.office.NotOfficeHolderException;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.ProgramMode;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Parteimitglied möchte Richtung, Wappen oder Entscheidungsmodus der Partei ändern. Steht die Partei auf
 * {@link ProgramMode#VOTE}, wird daraus ein Antrag, über den die Mitglieder abstimmen; bei
 * {@link ProgramMode#CHAIR} entscheidet nur der Vorsitz, und zwar sofort.
 */
public final class ChangePartyProgram {

    public record Command(PolityId party, ActorRef member, PartyProfile profile, Day day) {

        public Command {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(day, "day");
        }
    }

    /** Wenn abgestimmt wurde, der eröffnete Antrag, sonst leer (die Spitze hat sofort entschieden). */
    public record Result(Optional<ProposeMotion.Result> proposal) {
    }

    private final Aggregates aggregates;
    private final ProposeMotion proposeMotion;

    public ChangePartyProgram(Aggregates aggregates, IdGenerator ids, ProposeMotion proposeMotion) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        Objects.requireNonNull(ids, "ids");
        this.proposeMotion = Objects.requireNonNull(proposeMotion, "proposeMotion");
    }

    /**
     * @throws NotAPartyException       wenn die Polity keine Partei ist
     * @throws NotOfficeHolderException wenn ohne Abstimmungspflicht jemand anderes als der Vorsitz entscheiden will
     */
    public Result execute(Command command) {
        Polity party = aggregates.polity(command.party());
        if (party.kind() != PolityKind.PARTY) {
            throw new NotAPartyException(party.name());
        }
        boolean voteRequired = party.partyProfile().map(profile -> profile.mode() == ProgramMode.VOTE).orElse(false);
        if (voteRequired) {
            Motion.PartyProgram motion = new Motion.PartyProgram(command.profile());
            ProposeMotion.Result proposed = proposeMotion.execute(new ProposeMotion.Command(command.party(),
                    command.member(), motion, command.day()));
            return new Result(Optional.of(proposed));
        }
        if (!party.holds(command.member(), Office.MAYOR)) {
            throw new NotOfficeHolderException(command.member(), Office.MAYOR);
        }
        party.changePartyProfile(command.profile(), command.day());
        aggregates.commit(party);
        return new Result(Optional.empty());
    }
}
