package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;

/**
 * Prüfungen rund um das Bürgerrecht, für Use Cases außerhalb dieses Pakets.
 */
public final class Citizens {

    private Citizens() {
    }

    /** @throws NotACitizenException wenn der Akteur kein Bürger der Polity ist */
    public static void require(Polity polity, ActorRef actor) {
        AmendLaw.requireCitizen(polity, actor);
    }
}
