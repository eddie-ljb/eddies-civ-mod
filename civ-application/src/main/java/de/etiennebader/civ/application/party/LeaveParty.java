package de.etiennebader.civ.application.party;

import de.etiennebader.civ.application.polity.DissolvePolity;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Person verlässt ihre Partei und verliert dort ihre Ämter. Geht das letzte Mitglied, löst sich die Partei auf;
 * ihre Kasse geht an dieses Mitglied.
 */
public final class LeaveParty {

    public record Command(ActorRef member, Day day) {

        public Command {
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(day, "day");
        }
    }

    /** @param dissolved ob die Partei mit diesem Austritt aufgelöst wurde */
    public record Result(PolityId party, boolean dissolved) {
    }

    private final Aggregates aggregates;
    private final DissolvePolity dissolvePolity;

    public LeaveParty(Aggregates aggregates, DissolvePolity dissolvePolity) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.dissolvePolity = Objects.requireNonNull(dissolvePolity, "dissolvePolity");
    }

    /**
     * @throws NotInPartyException wenn die Person keiner Partei angehört
     */
    public Result execute(Command command) {
        Polity party = Parties.partyOf(aggregates.repositories().polities().findAll(), command.member())
                .orElseThrow(NotInPartyException::new);
        if (party.memberCount() <= 1) {
            dissolvePolity.execute(new DissolvePolity.Command(party.id(), command.member(), command.day()));
            return new Result(party.id(), true);
        }
        party.leave(command.member(), command.day());
        aggregates.commit(party);
        return new Result(party.id(), false);
    }
}
