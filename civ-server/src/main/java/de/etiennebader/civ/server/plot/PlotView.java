package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.land.PlotType;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Grundstück, wie Menüs und Meldungen es zeigen.
 *
 * @param holder       Name des Inhabers
 * @param townLand     ob es der Gemeinde selbst gehört
 * @param heldByViewer ob es dem betrachtenden Spieler gehört
 */
public record PlotView(PolityId town, String townName, PlotId id, PlotType type, Area area, String holder,
                       boolean townLand, boolean heldByViewer, long holderSince, Optional<Money> askingPrice) {

    public PlotView {
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(townName, "townName");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(askingPrice, "askingPrice");
    }
}
