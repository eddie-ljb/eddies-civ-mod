package de.etiennebader.civ.application.civil;

import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;

/**
 * Eine Postanschrift, z. B. „Hauptstraße 3, 41237 Nordhain“.
 *
 * @param number Hausnummer; ungerade auf der einen, gerade auf der anderen Straßenseite
 */
public record Address(PolityId town, String townName, String postalCode, String street, int number, PlotId plot) {

    public Address {
        Objects.requireNonNull(town, "town");
        Objects.requireNonNull(townName, "townName");
        Objects.requireNonNull(postalCode, "postalCode");
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(plot, "plot");
    }

    /** Einzeilig: „Straße Nr, PLZ Ort“. */
    public String line() {
        return street + " " + number + ", " + postalCode + " " + townName;
    }
}
