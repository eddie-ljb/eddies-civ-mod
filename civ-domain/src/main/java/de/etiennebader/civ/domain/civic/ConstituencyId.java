package de.etiennebader.civ.domain.civic;

import de.etiennebader.civ.domain.shared.IdGenerator;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Kennung eines Wahlkreises.
 */
public record ConstituencyId(UUID value) {

    public ConstituencyId {
        Objects.requireNonNull(value, "value");
    }

    public static ConstituencyId generate(IdGenerator ids) {
        return new ConstituencyId(ids.next());
    }

    /**
     * Der Wahlkreis als Bezirksschlüssel in Wahlergebnissen. Wahlergebnisse schlüsseln Bezirke nach {@link PolityId};
     * ein Wahlkreis teilt sich dafür seine Kennung, die nie mit einer Polity zusammenfällt.
     */
    public PolityId asDistrict() {
        return new PolityId(value);
    }
}
