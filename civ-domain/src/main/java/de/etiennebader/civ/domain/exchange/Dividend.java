package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Ausschüttung an die Aktionäre.
 *
 * @param perShare Einheiten je Aktie
 * @param total    insgesamt ausgezahlt
 */
public record Dividend(PolityId company, long perShare, long total, Day day) {

    public Dividend {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(day, "day");
        if (perShare <= 0 || total <= 0) {
            throw new IllegalArgumentException("Ungültige Dividende");
        }
    }
}
