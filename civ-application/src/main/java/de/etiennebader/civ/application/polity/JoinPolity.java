package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Akteur tritt einer Gemeinde bei. In Phase 0 ohne Zustimmung der Gemeinde.
 */
public final class JoinPolity {

    public record Command(PolityId polity, ActorRef member, MembershipType type, Day day) {

        public Command {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(member, "member");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(day, "day");
        }
    }

    private final Aggregates aggregates;
    private final MunicipalMembershipRule membershipRule;

    public JoinPolity(Aggregates aggregates) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.membershipRule = new MunicipalMembershipRule(aggregates.repositories().polities());
    }

    /**
     * @throws MemberOfOtherPolityException wenn der Akteur bereits einer Gemeinde angehört
     */
    public void execute(Command command) {
        Polity polity = aggregates.polity(command.polity());
        membershipRule.requireNoMunicipality(command.member());
        polity.admit(command.member(), command.type(), command.day());
        aggregates.commit(polity);
    }
}
