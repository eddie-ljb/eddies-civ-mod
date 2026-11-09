package de.etiennebader.civ.server.market;

import java.util.Objects;

/**
 * Was der Markt über ein Item wissen muss, ohne Minecraft zu kennen.
 *
 * @param id             Registry-Name, z. B. {@code minecraft:oak_log}
 * @param translationKey Sprachschlüssel des Item-Namens, z. B. {@code block.minecraft.oak_log}
 * @param rarity         Seltenheit laut Spiel
 * @param maxStackSize   größte Stapelgröße (1 bei Werkzeugen und Rüstung)
 * @param block          ob es ein platzierbarer Block ist
 * @param modified       ob der Stapel verändert ist (beschädigt, verzaubert, umbenannt, mit Inhalt);
 *                       solche Stapel kauft der Markt nicht, damit niemand versehentlich Wertvolles verliert
 */
public record ItemTraits(String id, String translationKey, Rarity rarity, int maxStackSize, boolean block,
                         boolean modified) {

    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC }

    public ItemTraits {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(rarity, "rarity");
        if (maxStackSize < 1) {
            throw new IllegalArgumentException("maxStackSize muss mindestens 1 sein: " + maxStackSize);
        }
    }

    /** Der Name ohne Namensraum, z. B. {@code oak_log}. */
    public String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
