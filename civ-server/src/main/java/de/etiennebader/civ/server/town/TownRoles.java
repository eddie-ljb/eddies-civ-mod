package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.Optional;

/**
 * Ämter in einer Gemeinde. Bis es Wahlen gibt, ist der dienstälteste Bürger Bürgermeister;
 * das ist anfangs der Gründer.
 */
public final class TownRoles {

    private TownRoles() {
    }

    public static Optional<ActorRef> mayorOf(PolityView polity) {
        return polity.members().stream()
                .filter(membership -> membership.type() == MembershipType.CITIZEN)
                .map(Membership::member)
                .findFirst();
    }

    public static boolean isMayor(PolityView polity, ActorRef actor) {
        return mayorOf(polity).filter(actor::equals).isPresent();
    }

    public static boolean isCitizen(PolityView polity, ActorRef actor) {
        return polity.membershipOf(actor).map(membership -> membership.type() == MembershipType.CITIZEN).orElse(false);
    }
}
