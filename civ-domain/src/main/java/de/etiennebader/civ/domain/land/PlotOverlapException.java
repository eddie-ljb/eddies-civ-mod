package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Ein Grundstück überlappt ein bestehendes Grundstück.
 */
public final class PlotOverlapException extends DomainException {

    public PlotOverlapException(PlotId existingPlot) {
        super(ErrorCode.PLOT_OVERLAP, existingPlot);
    }
}
