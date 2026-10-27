package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Alles, was seit dem letzten Speichern geändert wurde, plus die Events dieser Zeit für das Audit-Log.
 *
 * <p>Ein Schlüssel sagt nur „hier hat sich etwas geändert“. Den aktuellen Stand liest
 * {@link StateDelta#capture} aus dem Arbeitsstand; fehlt der Eintrag dort, wird er gelöscht.
 * Die Mengen behalten die Reihenfolge der ersten Änderung.
 */
public record ChangeSet(Set<PolityId> polities, Set<ChunkCoord> chunks, Set<PlotKey> plots,
                        Set<AccountKey> accounts, Set<CurrencyId> currencies, List<DomainEvent> events) {

    public ChangeSet {
        polities = ordered(polities, "polities");
        chunks = ordered(chunks, "chunks");
        plots = ordered(plots, "plots");
        accounts = ordered(accounts, "accounts");
        currencies = ordered(currencies, "currencies");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }

    private static <T> Set<T> ordered(Set<T> values, String name) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(values, name)));
    }

    public static ChangeSet empty() {
        return new ChangeSet(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of());
    }

    public boolean isEmpty() {
        return polities.isEmpty() && chunks.isEmpty() && plots.isEmpty() && accounts.isEmpty()
                && currencies.isEmpty() && events.isEmpty();
    }
}
