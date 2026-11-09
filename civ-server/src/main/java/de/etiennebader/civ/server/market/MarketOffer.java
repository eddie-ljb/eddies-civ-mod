package de.etiennebader.civ.server.market;

import de.etiennebader.civ.domain.market.Commodity;

import java.util.Objects;

/**
 * Eine Ware, die der Servermarkt auf Lager hat.
 *
 * @param stock heutiger Bestand
 */
public record MarketOffer(Commodity commodity, long stock) {

    public MarketOffer {
        Objects.requireNonNull(commodity, "commodity");
    }
}
