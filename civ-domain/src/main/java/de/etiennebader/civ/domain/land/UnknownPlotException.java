package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Grundstück existiert nicht.
 */
public final class UnknownPlotException extends DomainException {

    public UnknownPlotException(PlotId plot) {
        super(ErrorCode.UNKNOWN_PLOT, plot);
    }
}
