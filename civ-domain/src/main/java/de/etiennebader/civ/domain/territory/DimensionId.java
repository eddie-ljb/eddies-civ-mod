package de.etiennebader.civ.domain.territory;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Kennung einer Dimension im Format {@code namespace:pfad}, z. B. {@code minecraft:overworld}.
 */
public record DimensionId(String value) {

    // Muss vor den Konstanten stehen, weil deren Konstruktor das Muster bereits verwendet.
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final DimensionId OVERWORLD = new DimensionId("minecraft:overworld");
    public static final DimensionId NETHER = new DimensionId("minecraft:the_nether");
    public static final DimensionId END = new DimensionId("minecraft:the_end");

    public DimensionId {
        Objects.requireNonNull(value, "value");
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Ungültige Dimensions-ID: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
