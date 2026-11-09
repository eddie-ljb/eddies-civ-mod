package de.etiennebader.civ.application.inmemory;

import de.etiennebader.civ.domain.market.Market;
import de.etiennebader.civ.domain.market.MarketRepository;
import de.etiennebader.civ.domain.shared.CurrencyId;

import java.util.Objects;

public final class InMemoryMarketRepository implements MarketRepository {

    private Market market = Market.open(CurrencyId.EMERALD);

    @Override
    public Market load() {
        return market;
    }

    @Override
    public void save(Market market) {
        this.market = Objects.requireNonNull(market, "market");
    }
}
