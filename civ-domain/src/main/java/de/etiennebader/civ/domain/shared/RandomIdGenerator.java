package de.etiennebader.civ.domain.shared;

import java.util.UUID;

/**
 * Erzeugt zufällige Version-4-UUIDs. Für den Spielbetrieb.
 */
public final class RandomIdGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
