package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Objects;

/**
 * Ein zusätzlicher Heimat-Hafen einer Polity. Der erste Hafen (Slot 1) ist immer das Gemeindehaus der Polity und
 * wird nicht hier gespeichert; hier liegen nur die Slots {@link #FIRST_EXTRA_SLOT} bis {@link #LAST_EXTRA_SLOT}.
 *
 * @param polity   Besitzerin des Hafens
 * @param slot     2 oder 3
 * @param position Ziel des Teleports
 */
public record HomePort(PolityId polity, int slot, BlockPosition position) {

    public static final int FIRST_EXTRA_SLOT = 2;
    public static final int LAST_EXTRA_SLOT = 3;

    public HomePort {
        Objects.requireNonNull(polity, "polity");
        Objects.requireNonNull(position, "position");
        if (slot < FIRST_EXTRA_SLOT || slot > LAST_EXTRA_SLOT) {
            throw new IllegalArgumentException("Zusätzliche Häfen gibt es nur in den Slots " + FIRST_EXTRA_SLOT
                    + " und " + LAST_EXTRA_SLOT);
        }
    }
}
