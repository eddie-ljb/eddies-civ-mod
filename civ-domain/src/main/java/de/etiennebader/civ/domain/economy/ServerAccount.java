package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.UUID;

/**
 * Das Konto des Servers: Hierhin fließen Gründungsgebühren. Es gehört keiner Gemeinde, keinem Spieler und keiner
 * Firma und taucht deshalb in keiner Rangliste auf.
 */
public final class ServerAccount {

    /** Feste Kennung, die keiner echten Polity zugeteilt wird. */
    public static final ActorRef REF = new PolityRef(new PolityId(UUID.fromString("00000000-0000-0000-5e12-e12000000001")));

    private ServerAccount() {
    }

    public static boolean is(ActorRef actor) {
        return REF.equals(actor);
    }
}
