package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Person tritt einer Partei bei; wer schon in einer ist, muss erst austreten.
 */
public final class JoinParty {

    public record Command(PolityId party, ActorRef member, Day day) {

        public Command {
            Objects.requireNonNull(party, "party");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;

    public JoinParty(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
    }

    /**
     * @throws NotAPartyException      wenn die Polity keine Partei ist
     * @throws AlreadyInPartyException wenn die Person schon einer Partei angehört
     */
    public void execute(Command command) {
        Polity party = aggregates.polity(command.party());
        if (party.kind() != PolityKind.PARTY) {
            throw new NotAPartyException(party.name());
        }
        Parties.partyOf(aggregates.repositories().polities().findAll(), command.member()).ifPresent(current -> {
            throw new AlreadyInPartyException(current.name());
        });
        party.admit(command.member(), MembershipType.CITIZEN, command.day());
        aggregates.commit(party);
    }
}
