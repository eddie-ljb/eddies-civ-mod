package de.etiennebader.civ.application.party;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;

import java.util.Collection;
import java.util.Optional;

/**
 * Abfragen über Parteien.
 */
public final class Parties {

    private Parties() {
    }

    /** Die Partei, der ein Akteur angehört. */
    public static Optional<Polity> partyOf(Collection<Polity> polities, ActorRef actor) {
        return polities.stream()
                .filter(polity -> polity.kind() == PolityKind.PARTY && polity.isMember(actor))
                .findFirst();
    }
}
