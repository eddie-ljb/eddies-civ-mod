package de.etiennebader.civ.application.economy;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.application.support.Aggregates;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.economy.Currency;
import de.etiennebader.civ.domain.economy.CurrencyRegistry;
import de.etiennebader.civ.domain.economy.ExchangeRate;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * Eigene Währungen: erstellen, Wechselkurs, Aussehen (Item) und Zentralbank (Leitzins, Vorsitz), dazu die
 * Standardwährung einer Polity, die ein Gesetz oder die Verfassung per {@link StandardParameters#DEFAULT_CURRENCY}
 * festlegt.
 */
public final class CurrencyUseCases {

    private final Aggregates aggregates;
    private final WorldQueries queries;

    public CurrencyUseCases(Aggregates aggregates, WorldQueries queries) {
        this.aggregates = Objects.requireNonNull(aggregates, "aggregates");
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    public Currency create(CurrencyId id, String name, String symbol, ExchangeRate rate, ActorRef issuer,
                           String itemId, Day day) {
        CurrencyRegistry registry = aggregates.currencies();
        Currency created = registry.create(id, name, symbol, rate, issuer, itemId, day);
        aggregates.commit(registry);
        return created;
    }

    public Currency changeRate(CurrencyId id, ExchangeRate rate, ActorRef actor, Day day) {
        CurrencyRegistry registry = aggregates.currencies();
        Currency updated = registry.changeRate(id, rate, actor, day);
        aggregates.commit(registry);
        return updated;
    }

    public Currency appointChair(CurrencyId id, ActorRef chair, ActorRef actor, Day day) {
        CurrencyRegistry registry = aggregates.currencies();
        Currency updated = registry.appointChair(id, chair, actor, day);
        aggregates.commit(registry);
        return updated;
    }

    public Currency setItem(CurrencyId id, String itemId, ActorRef actor, Day day) {
        CurrencyRegistry registry = aggregates.currencies();
        Currency updated = registry.setItem(id, itemId, actor, day);
        aggregates.commit(registry);
        return updated;
    }

    /**
     * Setzt den Leitzins. Ein Gesetz der Polity des Vorsitzenden kann ihn über
     * {@link StandardParameters#CENTRAL_BANK_MAX_BASE_RATE_PERCENT} nach oben begrenzen.
     *
     * @throws de.etiennebader.civ.domain.credit.CreditException wenn der Leitzins über der gesetzlichen Grenze liegt
     */
    public Currency setBaseRate(CurrencyId id, int percent, ActorRef actor, Day day) {
        long limit = Jurisdiction.value(queries, actor, StandardParameters.CENTRAL_BANK_MAX_BASE_RATE_PERCENT);
        if (percent > limit) {
            throw de.etiennebader.civ.domain.credit.CreditException.interestAboveLimit(percent, limit);
        }
        CurrencyRegistry registry = aggregates.currencies();
        Currency updated = registry.setBaseRate(id, percent, actor, day);
        aggregates.commit(registry);
        return updated;
    }

    /** Rechnet in eine andere Währung um (Smaragd als gemeinsamer Nenner). */
    public Money convert(Money amount, CurrencyId target) {
        return queries.currencies().convert(amount, target);
    }

    /**
     * Die Standardwährung einer Polity: die per Gesetz oder Verfassung gewählte, sofern sie existiert, sonst die
     * Währung, mit der die Polity gegründet wurde.
     */
    public CurrencyId defaultCurrencyOf(PolityView polity) {
        String code = queries.effectiveLaws(polity).valueOf(StandardParameters.DEFAULT_CURRENCY);
        if (!code.isEmpty()) {
            CurrencyId chosen = new CurrencyId(code);
            if (queries.currencies().find(chosen).isPresent()) {
                return chosen;
            }
        }
        return polity.currency();
    }

    /** Standardwährung des Gebiets eines Akteurs; ohne Gemeinde die übergebene Rückfallwährung. */
    public CurrencyId defaultCurrencyFor(ActorRef actor, CurrencyId fallback) {
        Optional<PolityView> polity = Jurisdiction.of(queries, actor);
        return polity.map(this::defaultCurrencyOf).orElse(fallback);
    }
}
