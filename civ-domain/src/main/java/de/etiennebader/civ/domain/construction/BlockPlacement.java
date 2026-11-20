package de.etiennebader.civ.domain.construction;

import java.util.Objects;

/**
 * Ein Block eines {@link Blueprint}, relativ zum Ursprung des Bauauftrags (0,0,0 ist der Ursprung selbst).
 *
 * @param dx    Versatz nach Osten
 * @param dy    Versatz nach oben
 * @param dz    Versatz nach Süden
 * @param block Block-Kennung, z. B. {@code minecraft:oak_planks}
 */
public record BlockPlacement(int dx, int dy, int dz, String block) {

    public BlockPlacement {
        block = Objects.requireNonNull(block, "block").strip();
        if (block.isEmpty() || !block.contains(":")) {
            throw new IllegalArgumentException("Block-Kennung ungültig: " + block);
        }
    }
}
