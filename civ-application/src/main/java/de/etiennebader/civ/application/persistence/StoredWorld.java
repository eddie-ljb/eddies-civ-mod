package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.domain.economy.Ledger;
import de.etiennebader.civ.domain.land.LandRegistry;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.territory.TerritoryRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Der vollständige gespeicherte Zustand: alle Aggregate, wie sie aus dem {@link StateStore} kommen,
 * dazu Spielernamen und Kalender.
 *
 * @param polities in Gründungsreihenfolge
 * @param land     ein Grundbuch je Polity
 */
public record StoredWorld(List<Polity> polities, TerritoryRegistry territory, List<LandRegistry> land, Ledger ledger,
                          List<KnownPlayer> players, CalendarState calendar) {

    public StoredWorld {
        polities = List.copyOf(Objects.requireNonNull(polities, "polities"));
        Objects.requireNonNull(territory, "territory");
        land = List.copyOf(Objects.requireNonNull(land, "land"));
        Objects.requireNonNull(ledger, "ledger");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        Objects.requireNonNull(calendar, "calendar");
    }
}
