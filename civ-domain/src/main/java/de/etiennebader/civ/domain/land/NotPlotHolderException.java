package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;

/**
 * Der Akteur ist nicht Inhaber des Grundstücks.
 */
public final class NotPlotHolderException extends DomainException {

    public NotPlotHolderException(ActorRef actor, PlotId plot) {
        super(ErrorCode.NOT_PLOT_HOLDER, actor, plot);
    }
}
