package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Optional;

/**
 * Woher die Schutzregeln die Rechte eines Grundstücks nehmen, die der Inhaber eingestellt hat.
 */
@FunctionalInterface
public interface PlotRightsSource {

    /** Ohne Einstellungen: überall gilt der Standard. */
    PlotRightsSource NONE = (polity, plot) -> Optional.empty();

    Optional<PlotRights> rightsOf(PolityId polity, PlotId plot);
}
