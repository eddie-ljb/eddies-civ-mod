package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.exchange.StockExchange;
import de.etiennebader.civ.domain.exchange.StockExchangeRepository;

import java.util.Objects;

/**
 * Hält die Börse im Arbeitsspeicher.
 */
public final class InMemoryExchangeRepository implements StockExchangeRepository {

    private StockExchange value = StockExchange.empty();

    @Override
    public StockExchange load() {
        return value;
    }

    @Override
    public void save(StockExchange value) {
        this.value = Objects.requireNonNull(value, "value");
    }
}
