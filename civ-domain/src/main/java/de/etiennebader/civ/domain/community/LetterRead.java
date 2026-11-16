package de.etiennebader.civ.domain.community;

import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.LetterId;

import java.util.Objects;

/**
 * Ein Brief wurde gelesen.
 */
public record LetterRead(PlayerRef actor, LetterId letter, Day occurredOn) implements CommunityEvent {

    public LetterRead {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(letter, "letter");
        Objects.requireNonNull(occurredOn, "occurredOn");
    }
}
