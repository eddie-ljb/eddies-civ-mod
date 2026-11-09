package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Unveränderlicher Zustand eines Grundstücks, z. B. zum Speichern und Wiederherstellen.
 */
public record PlotSnapshot(PlotId id, Area area, PlotType type, ActorRef holder, Day holderSince,
                           Optional<Money> askingPrice) {

    public PlotSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(holderSince, "holderSince");
        Objects.requireNonNull(askingPrice, "askingPrice");
    }

    /** Ein Grundstück, das nicht zum Verkauf steht. */
    public PlotSnapshot(PlotId id, Area area, PlotType type, ActorRef holder, Day holderSince) {
        this(id, area, type, holder, holderSince, Optional.empty());
    }
}
