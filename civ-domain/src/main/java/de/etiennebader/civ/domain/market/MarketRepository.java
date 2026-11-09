package de.etiennebader.civ.domain.market;

/**
 * Speicher des Servermarkts. Es gibt genau einen Markt je Server.
 */
public interface MarketRepository {

    Market load();

    void save(Market market);
}
