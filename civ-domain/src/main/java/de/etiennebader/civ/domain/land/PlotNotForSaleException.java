package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Grundstück steht nicht zum Verkauf.
 */
public final class PlotNotForSaleException extends DomainException {

    public PlotNotForSaleException(PlotId plot) {
        super(ErrorCode.PLOT_NOT_FOR_SALE, plot);
    }
}
