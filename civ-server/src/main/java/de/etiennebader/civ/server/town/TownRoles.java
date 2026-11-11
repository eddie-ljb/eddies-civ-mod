package de.etiennebader.civ.server.town;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;

import java.util.Optional;

/**
 * Wer in einer Gemeinde was darf. Bürgermeister ist der gewählte Inhaber des Amts, sonst der dienstälteste Bürger
 * (anfangs der Gründer). Schatzmeister und Bauamt teilen sich Aufgaben mit dem Bürgermeister.
 */
public final class TownRoles {

    private TownRoles() {
    }

    public static Optional<ActorRef> mayorOf(PolityView polity) {
        return polity.mayor();
    }

    public static boolean isMayor(PolityView polity, ActorRef actor) {
        return polity.holds(actor, Office.MAYOR);
    }

    /** Geld aus der Gemeindekasse nehmen: Bürgermeister und Schatzmeister. */
    public static boolean mayManageTreasury(PolityView polity, ActorRef actor) {
        return isMayor(polity, actor) || polity.holds(actor, Office.TREASURER);
    }

    /** Grundstücke der Gemeinde anlegen, anbieten, vergeben und auflösen: Bürgermeister und Bauamt. */
    public static boolean mayManagePlots(PolityView polity, ActorRef actor) {
        return isMayor(polity, actor) || polity.holds(actor, Office.PLANNER);
    }

    public static boolean isCitizen(PolityView polity, ActorRef actor) {
        return polity.membershipOf(actor).map(membership -> membership.type() == MembershipType.CITIZEN).orElse(false);
    }
}
