package de.etiennebader.civ.domain.exchange;

public interface StockExchangeRepository {

    StockExchange load();

    void save(StockExchange exchange);
}
