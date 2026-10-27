package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.day.CalendarState;
import de.etiennebader.civ.application.directory.KnownPlayer;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.economy.CurrencyTotals;
import de.etiennebader.civ.domain.economy.LedgerView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotSnapshot;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.domain.territory.TerritoryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Die zu schreibenden Werte eines {@link ChangeSet}: eine unveränderliche Kopie, die auf dem
 * Spielthread erfasst und danach in einem anderen Thread geschrieben werden kann.
 *
 * <p>Ein leerer Wert bedeutet „existiert nicht mehr“ und führt zum Löschen. Alle Listen behalten
 * die Reihenfolge der ersten Änderung.
 */
public record StateDelta(List<PolityChange> polities, List<ClaimChange> claims, List<PlotChange> plots,
                         List<BalanceChange> balances, List<CurrencyTotals> currencies, List<DomainEvent> events,
                         List<KnownPlayer> players, CalendarState calendar) {

    public StateDelta {
        polities = List.copyOf(Objects.requireNonNull(polities, "polities"));
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        plots = List.copyOf(Objects.requireNonNull(plots, "plots"));
        balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
        currencies = List.copyOf(Objects.requireNonNull(currencies, "currencies"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        Objects.requireNonNull(calendar, "calendar");
    }

    /** Aktueller Stand einer geänderten Polity; leer, wenn sie aufgelöst wurde. */
    public record PolityChange(PolityId id, Optional<PolitySnapshot> polity) {

        public PolityChange {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(polity, "polity");
        }
    }

    /** Aktueller Claim eines geänderten Chunks; leer, wenn er freigegeben wurde. */
    public record ClaimChange(ChunkCoord chunk, Optional<Claim> claim) {

        public ClaimChange {
            Objects.requireNonNull(chunk, "chunk");
            Objects.requireNonNull(claim, "claim");
        }
    }

    /** Aktueller Stand eines geänderten Grundstücks; leer, wenn es aufgelöst wurde. */
    public record PlotChange(PlotKey key, Optional<PlotSnapshot> plot) {

        public PlotChange {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(plot, "plot");
        }
    }

    /** Neuer Kontostand; ein Stand von null bedeutet, das Konto wird nicht mehr geführt. */
    public record BalanceChange(AccountKey account, Money balance) {

        public BalanceChange {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(balance, "balance");
        }
    }

    /**
     * Liest die aktuellen Werte aller geänderten Einträge. Muss im Thread laufen, der den
     * Arbeitsstand verändert, damit die Kopie in sich stimmig ist.
     */
    public static StateDelta capture(ChangeSet changes, WorldQueries current, List<KnownPlayer> players,
                                     CalendarState calendar) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(current, "current");

        List<PolityChange> polities = new ArrayList<>();
        for (PolityId id : changes.polities()) {
            polities.add(new PolityChange(id, current.polity(id).map(PolitySnapshot::of)));
        }

        TerritoryView territory = current.territory();
        List<ClaimChange> claims = new ArrayList<>();
        for (ChunkCoord chunk : changes.chunks()) {
            claims.add(new ClaimChange(chunk, territory.claimAt(chunk)));
        }

        List<PlotChange> plots = new ArrayList<>();
        for (PlotKey key : changes.plots()) {
            Optional<PlotSnapshot> plot = current.polity(key.polity()).isPresent()
                    ? current.land(key.polity()).plot(key.plot()).map(Plot::snapshot)
                    : Optional.empty();
            plots.add(new PlotChange(key, plot));
        }

        LedgerView ledger = current.ledger();
        List<BalanceChange> balances = new ArrayList<>();
        for (AccountKey account : changes.accounts()) {
            balances.add(new BalanceChange(account, ledger.balanceOf(account.account(), account.currency())));
        }
        List<CurrencyTotals> currencies = new ArrayList<>();
        for (CurrencyId currency : changes.currencies()) {
            currencies.add(new CurrencyTotals(currency, ledger.totalSupply(currency), ledger.cashInCirculation(currency)));
        }

        return new StateDelta(polities, claims, plots, balances, currencies, changes.events(), players, calendar);
    }
}
