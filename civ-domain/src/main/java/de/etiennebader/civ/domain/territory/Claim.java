package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Ein Eintrag im Hoheitsregister: Welche Polity herrscht seit wann und in welcher Art über einen Chunk.
 */
public record Claim(PolityId polity, ClaimType type, Day since) {

    public Claim {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(since, "since");
    }
}
