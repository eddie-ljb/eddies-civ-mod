package de.etiennebader.civ.domain.economy;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Register aller Währungen der Welt. Smaragd ist immer vorhanden und unveränderlich.
 * Eigene Währungen haben einen eindeutigen Code und ein eindeutiges Symbol; Wechselkurs,
 * Leitzins und Vorsitz ändert nur der {@link Currency#governor() Gouverneur} der Währung.
 */
public final class CurrencyRegistry extends AggregateRoot<CurrencyEvent> implements CurrencyView {

    private final Map<CurrencyId, Currency> currencies = new LinkedHashMap<>();

    private CurrencyRegistry() {
        Currency emerald = Currency.emerald();
        currencies.put(emerald.id(), emerald);
    }

    public static CurrencyRegistry empty() {
        return new CurrencyRegistry();
    }

    /** Stellt ein gespeichertes Register wieder her; Smaragd wird nie überschrieben. */
    public static CurrencyRegistry restore(Collection<Currency> stored) {
        CurrencyRegistry registry = new CurrencyRegistry();
        for (Currency currency : stored) {
            if (!currency.id().equals(CurrencyId.EMERALD)) {
                registry.currencies.put(currency.id(), currency);
            }
        }
        return registry;
    }

    /**
     * Erstellt eine neue Währung.
     *
     * @throws CurrencyAlreadyExistsException wenn Code oder Symbol schon vergeben sind
     */
    public Currency create(CurrencyId id, String name, String symbol, ExchangeRate rateToEmerald,
                           ActorRef issuer, Day day) {
        return create(id, name, symbol, rateToEmerald, issuer, null, day);
    }

    /** Wie {@link #create(CurrencyId, String, String, ExchangeRate, ActorRef, Day)}, mit eigenem Auszahlungs-Item. */
    public Currency create(CurrencyId id, String name, String symbol, ExchangeRate rateToEmerald,
                           ActorRef issuer, String itemId, Day day) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(issuer, "issuer");
        Currency created = new Currency(id, name, symbol, rateToEmerald, Optional.of(issuer),
                Optional.empty(), 0, Optional.ofNullable(itemId));
        if (currencies.containsKey(id)) {
            throw new CurrencyAlreadyExistsException(id.code());
        }
        for (Currency other : currencies.values()) {
            if (other.symbol().equals(created.symbol())) {
                throw new CurrencyAlreadyExistsException(created.symbol());
            }
        }
        currencies.put(id, created);
        raise(new CurrencyEvent.Created(id, day));
        return created;
    }

    /** Ändert den Wechselkurs zu Smaragd. */
    public Currency changeRate(CurrencyId id, ExchangeRate rate, ActorRef actor, Day day) {
        Currency updated = requireGoverned(id, actor).withRate(rate);
        currencies.put(id, updated);
        raise(new CurrencyEvent.RateChanged(id, day));
        return updated;
    }

    /** Ernennt einen (neuen) Zentralbank-Vorsitzenden. */
    public Currency appointChair(CurrencyId id, ActorRef newChair, ActorRef actor, Day day) {
        Currency updated = requireGoverned(id, actor).withChair(newChair);
        currencies.put(id, updated);
        raise(new CurrencyEvent.ChairAppointed(id, day));
        return updated;
    }

    /** Legt das Item fest, in dem die Währung am Bankschalter ausgezahlt wird (Aussehen der Währung). */
    public Currency setItem(CurrencyId id, String itemId, ActorRef actor, Day day) {
        Currency updated = requireGoverned(id, actor).withItem(itemId);
        currencies.put(id, updated);
        raise(new CurrencyEvent.ItemChanged(id, day));
        return updated;
    }

    /** Setzt den Leitzins in Prozent (0 bis {@link Currency#MAX_BASE_RATE_PERCENT}). */
    public Currency setBaseRate(CurrencyId id, int percent, ActorRef actor, Day day) {
        Currency updated = requireGoverned(id, actor).withBaseRatePercent(percent);
        currencies.put(id, updated);
        raise(new CurrencyEvent.BaseRateChanged(id, day));
        return updated;
    }

    /**
     * Rechnet einen Betrag in eine andere Währung um (abgerundet, Smaragd als gemeinsamer Nenner).
     *
     * @throws UnknownCurrencyException wenn eine der Währungen nicht registriert ist
     * @throws DomainException          bei negativem Betrag
     * @throws ArithmeticException      bei Überlauf
     */
    @Override
    public Money convert(Money amount, CurrencyId target) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) {
            throw new NegativeConversionException(amount.amount());
        }
        Currency from = require(amount.currency());
        Currency to = require(target);
        return Money.of(from.convertTo(amount.amount(), to), target);
    }

    @Override
    public Optional<Currency> find(CurrencyId id) {
        return Optional.ofNullable(currencies.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public Currency require(CurrencyId id) {
        return find(id).orElseThrow(() -> new UnknownCurrencyException(id));
    }

    @Override
    public List<Currency> all() {
        return List.copyOf(currencies.values());
    }

    private Currency requireGoverned(CurrencyId id, ActorRef actor) {
        Currency currency = require(id);
        if (currency.governor().isEmpty()) {
            throw new CurrencyProtectedException(id);
        }
        if (!currency.governor().get().equals(actor)) {
            throw new NotCurrencyGovernorException(id, actor);
        }
        return currency;
    }

    private static final class NegativeConversionException extends DomainException {
        NegativeConversionException(long amount) {
            super(ErrorCode.NEGATIVE_CONVERSION_AMOUNT, amount);
        }
    }
}
