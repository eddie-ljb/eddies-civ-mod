package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Eine Freundschaftsanfrage wurde gestellt.
 */
public record FriendRequested(PlayerRef actor, PlayerRef to, Day occurredOn) implements CommunityEvent {

    public FriendRequested {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
