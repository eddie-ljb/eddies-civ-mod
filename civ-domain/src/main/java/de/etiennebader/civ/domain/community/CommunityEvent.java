package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.DomainEvent;

/**
 * Alle Events des {@link Community}-Aggregats.
 */
public sealed interface CommunityEvent extends DomainEvent
        permits LetterSent, LetterRead, FriendRequested, FriendshipChanged, ChatPosted {

    /** Wer gehandelt hat. */
    PlayerRef actor();
}
