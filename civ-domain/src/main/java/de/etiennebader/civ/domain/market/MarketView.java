package de.etiennebader.civ.domain.market;

import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;

/**
 * Lesender Zugriff auf den Servermarkt.
 */
public interface MarketView {

    CurrencyId currency();

    /** Heutiger Lagerbestand einer Ware nach Verbrauch. */
    long stockOf(Commodity commodity, Day today);

    /** Preise für ein Stück bei heutigem Bestand. */
    Quote quote(Commodity commodity, long basePrice, PriceLevel level, Day today);

    /** Gespeicherte Bestände aller Waren, nach Ware sortiert. */
    List<CommodityStock> stocks();
}
