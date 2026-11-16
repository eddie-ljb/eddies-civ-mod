package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Chatnachricht wurde geschrieben.
 */
public record ChatPosted(PlayerRef actor, long seq, boolean direct, Day occurredOn) implements CommunityEvent {

    public ChatPosted {
        Objects.requireNonNull(actor, "actor");

        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
