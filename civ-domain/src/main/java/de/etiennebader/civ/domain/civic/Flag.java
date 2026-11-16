package de.etiennebader.civ.domain.civic;

import java.util.List;
import java.util.Objects;

/**
 * Die Flagge einer Polity als Banner: Grundfarbe und Muster-Schichten, wie Minecraft sie speichert.
 *
 * @param base   Farbname wie {@code red}
 * @param layers Schichten von unten nach oben
 */
public record Flag(String base, List<Layer> layers) {

    public static final int MAX_LAYERS = 16;

    /** Eine Muster-Schicht, z. B. {@code minecraft:stripe_top} in {@code white}. */
    public record Layer(String pattern, String color) {

        public Layer {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(color, "color");
        }
    }

    public Flag {
        Objects.requireNonNull(base, "base");
        layers = List.copyOf(layers);
        if (layers.size() > MAX_LAYERS) {
            throw new IllegalArgumentException("Zu viele Schichten: " + layers.size());
        }
    }
}
