package de.etiennebader.civ.application.territory;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.territory.ChunkCoord;

/**
 * Ein Chunk kann nicht freigegeben werden, weil dort noch Grundstücke liegen.
 */
public final class ChunkHasPlotsException extends DomainException {

    public ChunkHasPlotsException(ChunkCoord chunk) {
        super(ErrorCode.CHUNK_HAS_PLOTS, chunk.x(), chunk.z());
    }
}
