package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Bund wurde gegründet, z. B. ein Land aus einer Gemeinde.
 *
 * @param level       Schlüssel der Ebene, z. B. {@code land}
 * @param constituent die erste Gliedkörperschaft
 * @param head        wer den Bund zunächst führt
 */
public record FederationFounded(PolityId polity, PolityName name, String level, PolityRef constituent, ActorRef head,
                                Day occurredOn) implements PolityEvent {

    public FederationFounded {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(constituent, "constituent");
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
