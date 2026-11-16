package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.land.Area;

import java.util.Objects;

/**
 * Eine benannte Straße im Gebiet einer Gemeinde: eine rechteckige Fläche, an der Grundstücke Hausnummern bekommen.
 */
public record Street(String name, Area area) {

    public static final int MAX_NAME = 32;

    public Street {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(area, "area");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Straßenname muss 1 bis " + MAX_NAME + " Zeichen haben");
        }
    }

    /** Ob die Straße in X-Richtung verläuft (sonst in Z-Richtung). */
    public boolean runsAlongX() {
        return area.width() >= area.depth();
    }
}
