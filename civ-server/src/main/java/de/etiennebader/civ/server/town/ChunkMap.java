package de.etiennebader.civ.server.town;

import java.util.List;
import java.util.Objects;

/**
 * Ausschnitt der Gebietskarte um einen Spieler, zeilenweise von Norden nach Süden, je Zeile von Westen nach Osten.
 */
public record ChunkMap(int width, int height, List<MapCell> cells) {

    public ChunkMap {
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        if (cells.size() != width * height) {
            throw new IllegalArgumentException("Karte braucht " + width * height + " Felder, hat " + cells.size());
        }
    }

    public MapCell at(int column, int row) {
        return cells.get(row * width + column);
    }
}
