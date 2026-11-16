package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Direktnachrichten gehen nur an Freunde.
 */
public final class NotFriendsException extends DomainException {

    public NotFriendsException(PlayerRef other) {
        super(ErrorCode.NOT_FRIENDS, other);
    }
}
