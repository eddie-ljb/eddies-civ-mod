package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.economy.CashDeposited;
import de.etiennebader.civ.domain.economy.CashWithdrawn;
import de.etiennebader.civ.domain.economy.FundsTransferred;
import de.etiennebader.civ.domain.economy.LedgerEvent;
import de.etiennebader.civ.domain.economy.MoneyBurned;
import de.etiennebader.civ.domain.economy.MoneyMinted;
import de.etiennebader.civ.domain.land.LandEvent;
import de.etiennebader.civ.domain.polity.PolityEvent;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.TerritoryEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merkt sich anhand der veröffentlichten Domain Events, welche Teile des Zustands seit dem letzten
 * Speichern geändert wurden, und sammelt die Events für das Audit-Log.
 *
 * <p>Wird als zusätzlicher {@link DomainEventPublisher} an die Application gehängt. Weil jede
 * Zustandsänderung ein Event erzeugt, entgeht dem Tracker keine Änderung.
 */
public final class ChangeTracker implements DomainEventPublisher {

    private final Set<PolityId> polities = new LinkedHashSet<>();
    private final Set<ChunkCoord> chunks = new LinkedHashSet<>();
    private final Set<PlotKey> plots = new LinkedHashSet<>();
    private final Set<AccountKey> accounts = new LinkedHashSet<>();
    private final Set<CurrencyId> currencies = new LinkedHashSet<>();
    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public void publish(List<? extends DomainEvent> published) {
        for (DomainEvent event : published) {
            track(event);
            events.add(event);
        }
    }

    private void track(DomainEvent event) {
        switch (event) {
            case PolityEvent polityEvent -> polities.add(polityEvent.polity());
            case TerritoryEvent territoryEvent -> chunks.add(territoryEvent.chunk());
            case LandEvent landEvent -> plots.add(new PlotKey(landEvent.polity(), landEvent.plot()));
            case LedgerEvent ledgerEvent -> trackLedger(ledgerEvent);
            default -> {
                // Events anderer Bereiche ändern keinen gespeicherten Zustand, landen aber im Audit-Log.
            }
        }
    }

    private void trackLedger(LedgerEvent event) {
        CurrencyId currency = event.amount().currency();
        switch (event) {
            case FundsTransferred transfer -> {
                accounts.add(new AccountKey(transfer.from(), currency));
                accounts.add(new AccountKey(transfer.to(), currency));
            }
            case CashWithdrawn withdrawal -> {
                accounts.add(new AccountKey(withdrawal.account(), currency));
                currencies.add(currency);
            }
            case CashDeposited deposit -> {
                accounts.add(new AccountKey(deposit.account(), currency));
                currencies.add(currency);
            }
            case MoneyMinted minted -> {
                accounts.add(new AccountKey(minted.to(), currency));
                currencies.add(currency);
            }
            case MoneyBurned burned -> {
                accounts.add(new AccountKey(burned.from(), currency));
                currencies.add(currency);
            }
        }
    }

    /** Ob seit dem letzten Abholen etwas passiert ist. */
    public boolean hasChanges() {
        return !events.isEmpty();
    }

    /** Liefert alle Änderungen seit dem letzten Aufruf und beginnt von vorn. */
    public ChangeSet takeChanges() {
        ChangeSet changes = new ChangeSet(polities, chunks, plots, accounts, currencies, events);
        polities.clear();
        chunks.clear();
        plots.clear();
        accounts.clear();
        currencies.clear();
        events.clear();
        return changes;
    }

    /**
     * Gibt Änderungen zurück, deren Speichern fehlgeschlagen ist. Sie werden beim nächsten Mal
     * erneut geschrieben; Events behalten ihre ursprüngliche Reihenfolge vor neueren.
     */
    public void requeue(ChangeSet failed) {
        polities.addAll(failed.polities());
        chunks.addAll(failed.chunks());
        plots.addAll(failed.plots());
        accounts.addAll(failed.accounts());
        currencies.addAll(failed.currencies());
        events.addAll(0, failed.events());
    }
}
