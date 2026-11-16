package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Freundschaft wurde geschlossen oder beendet.
 */
public record FriendshipChanged(PlayerRef actor, PlayerRef other, boolean friends, Day occurredOn) implements CommunityEvent {

    public FriendshipChanged {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(other, "other");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
