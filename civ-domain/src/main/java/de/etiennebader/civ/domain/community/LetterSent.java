package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.LetterId;

import java.util.Objects;

/**
 * Ein Brief wurde aufgegeben.
 */
public record LetterSent(PlayerRef actor, LetterId letter, PlayerRef to, Day occurredOn) implements CommunityEvent {

    public LetterSent {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(letter, "letter");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
