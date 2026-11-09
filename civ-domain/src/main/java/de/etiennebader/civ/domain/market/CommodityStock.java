package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.Day;

import java.util.Objects;

/**
 * Lagerbestand einer Ware, wie er am Tag {@code asOf} war. Der heutige Bestand ergibt sich daraus
 * durch den täglichen Verbrauch ({@link PriceModel#decayed}).
 */
public record CommodityStock(Commodity commodity, long quantity, Day asOf) {

    public CommodityStock {
        Objects.requireNonNull(commodity, "commodity");
        Objects.requireNonNull(asOf, "asOf");
        if (quantity < 0) {
            throw new IllegalArgumentException("Bestand darf nicht negativ sein: " + quantity);
        }
    }
}
