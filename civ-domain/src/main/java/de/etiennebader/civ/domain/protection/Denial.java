package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine verweigerte Handlung.
 *
 * @param polity die Gemeinde, deren Gebiet betroffen ist
 * @param owner  wem der Block gehört: die Gemeinde selbst oder der Inhaber des Grundstücks
 */
public record Denial(DenialReason reason, PolityId polity, ActorRef owner) {

    public Denial {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(owner, "owner");
    }
}
