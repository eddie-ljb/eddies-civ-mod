package de.etiennebader.civ.domain.territory;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Die Polity hat über den Chunk keine Hoheit.
 */
public final class NotSovereignException extends DomainException {

    public NotSovereignException(ChunkCoord chunk) {
        super(ErrorCode.NOT_SOVEREIGN, chunk.x(), chunk.z());
    }
}
