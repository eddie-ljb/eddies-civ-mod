package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Ein Spieler hat einem Freund Baurecht auf seinen Grundstücken gegeben oder es zurückgenommen.
 */
public record FriendTrustChanged(PlayerRef actor, PlayerRef friend, boolean trusted, Day occurredOn)
        implements CommunityEvent {

    public FriendTrustChanged {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(friend, "friend");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
