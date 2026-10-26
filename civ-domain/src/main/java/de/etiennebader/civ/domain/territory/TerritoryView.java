package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Optional;
import java.util.Set;

/**
 * Lesender Zugriff auf das Hoheitsregister, z. B. für Policies und das Grundbuch.
 */
public interface TerritoryView {

    Optional<Claim> claimAt(ChunkCoord chunk);

    default Optional<PolityId> sovereignOf(ChunkCoord chunk) {
        return claimAt(chunk).map(Claim::polity);
    }

    /** Alle Chunks unter der Hoheit der Polity, unabhängig von der Claim-Art. */
    Set<ChunkCoord> territoryOf(PolityId polity);

    int countOf(PolityId polity, ClaimType type);
}
