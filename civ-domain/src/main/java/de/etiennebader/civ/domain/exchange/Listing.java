package de.etiennebader.civ.domain.exchange;

import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine an der Börse notierte Firma.
 *
 * @param totalShares  ausgegebene Aktien
 * @param initialPrice Ausgabepreis je Aktie
 */
public record Listing(PolityId company, long totalShares, long initialPrice, Day listedOn) {

    public Listing {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(listedOn, "listedOn");
        if (totalShares <= 0 || initialPrice <= 0) {
            throw new IllegalArgumentException("Ungültige Notierung");
        }
    }

    Listing withShares(long shares) {
        return new Listing(company, shares, initialPrice, listedOn);
    }
}
