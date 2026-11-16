package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Das Grundstück lässt sich so nicht vermieten: Der Anbieter verfügt nicht darüber, oder es ist schon vermietet.
 */
public final class PlotNotLeasableException extends DomainException {

    public PlotNotLeasableException() {
        super(ErrorCode.PLOT_NOT_LEASABLE);
    }
}
