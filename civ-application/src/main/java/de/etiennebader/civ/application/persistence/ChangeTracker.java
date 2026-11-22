package de.etiennebader.civ.application.persistence;

import de.etiennebader.civ.domain.justice.JusticeEvent;
import de.etiennebader.civ.domain.civic.CivicEvent;
import de.etiennebader.civ.domain.credit.CreditEvent;
import de.etiennebader.civ.domain.economy.CurrencyEvent;
import de.etiennebader.civ.domain.production.ProductionEvent;
import de.etiennebader.civ.application.port.DomainEventPublisher;
import de.etiennebader.civ.domain.community.CommunityEvent;
import de.etiennebader.civ.domain.contract.ContractEvent;
import de.etiennebader.civ.domain.economy.CashDeposited;
import de.etiennebader.civ.domain.economy.CashWithdrawn;
import de.etiennebader.civ.domain.economy.FundsTransferred;
import de.etiennebader.civ.domain.economy.LedgerEvent;
import de.etiennebader.civ.domain.economy.MoneyBurned;
import de.etiennebader.civ.domain.economy.MoneyMinted;
import de.etiennebader.civ.domain.exchange.ExchangeEvent;
import de.etiennebader.civ.domain.land.LandEvent;
import de.etiennebader.civ.domain.market.Commodity;
import de.etiennebader.civ.domain.market.MarketEvent;
import de.etiennebader.civ.domain.media.PressEvent;
import de.etiennebader.civ.domain.polity.PolityEvent;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.DomainEvent;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shop.ShopEvent;
import de.etiennebader.civ.domain.territory.BlockPosition;
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
    private final Set<Commodity> commodities = new LinkedHashSet<>();
    private final Set<BlockPosition> shops = new LinkedHashSet<>();
    private final List<DomainEvent> events = new ArrayList<>();
    private boolean contracts;
    private boolean community;
    private boolean exchange;
    private boolean press;
    private boolean civic;
    private boolean justice;
    private boolean production;
    private boolean currencyRegistry;
    private boolean credit;

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
            case MarketEvent marketEvent -> commodities.add(marketEvent.commodity());
            case ShopEvent shopEvent -> shops.add(shopEvent.position());
            case ContractEvent ignored -> contracts = true;
            case CommunityEvent ignored -> community = true;
            case ExchangeEvent ignored -> exchange = true;
            case PressEvent ignored -> press = true;
            case CivicEvent ignored -> civic = true;
            case JusticeEvent ignored -> justice = true;
            case ProductionEvent ignored -> production = true;
            case CurrencyEvent ignored -> currencyRegistry = true;
            case CreditEvent ignored -> credit = true;
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
        ChangeSet changes = new ChangeSet(polities, chunks, plots, accounts, currencies, commodities, shops, events,
                contracts, community, exchange, press, civic, justice, production, currencyRegistry, credit);
        production = false;
        currencyRegistry = false;
        credit = false;
        civic = false;
        justice = false;
        contracts = false;
        community = false;
        exchange = false;
        press = false;
        polities.clear();
        chunks.clear();
        plots.clear();
        accounts.clear();
        currencies.clear();
        commodities.clear();
        shops.clear();
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
        commodities.addAll(failed.commodities());
        shops.addAll(failed.shops());
        contracts |= failed.contracts();
        community |= failed.community();
        exchange |= failed.exchange();
        press |= failed.press();
        civic |= failed.civic();
        justice |= failed.justice();
        production |= failed.production();
        currencyRegistry |= failed.currencyRegistry();
        credit |= failed.credit();
        events.addAll(0, failed.events());
    }
}
