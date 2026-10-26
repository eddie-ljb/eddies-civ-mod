package de.etiennebader.civ.domain.land;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Unveränderlicher Zustand eines Grundstücks, z. B. zum Speichern und Wiederherstellen.
 */
public record PlotSnapshot(PlotId id, Area area, PlotType type, ActorRef holder, Day holderSince) {

    public PlotSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(holderSince, "holderSince");
    }
}
