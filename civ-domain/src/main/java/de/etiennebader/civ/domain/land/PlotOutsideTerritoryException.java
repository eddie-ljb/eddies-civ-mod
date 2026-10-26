package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Ein Grundstück ragt über das Gebiet der Polity hinaus.
 */
public final class PlotOutsideTerritoryException extends DomainException {

    public PlotOutsideTerritoryException(ChunkCoord chunkOutside) {
        super(ErrorCode.PLOT_OUTSIDE_TERRITORY, chunkOutside.x(), chunkOutside.z());
    }
}
