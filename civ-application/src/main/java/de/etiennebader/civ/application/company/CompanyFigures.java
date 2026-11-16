package de.etiennebader.civ.application.company;

import de.etiennebader.civ.domain.exchange.Dividend;
import de.etiennebader.civ.domain.exchange.Listing;
import de.etiennebader.civ.domain.exchange.Quote;
import de.etiennebader.civ.domain.exchange.StockExchangeView;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;

import java.util.List;
import java.util.Objects;

/**
 * Kennzahlen einer notierten Firma in Einheiten.
 *
 * @param price         letzter Kurs je Aktie
 * @param previousClose Schlusskurs des Vortags; ohne Vortag der Ausgabepreis
 * @param bookValue     Kasse je Aktie
 * @param marketCap     Kurs mal Aktienzahl
 * @param dividends30   Dividende je Aktie in den letzten 30 Tagen
 * @param momentum      Kursänderung über bis zu 5 Tage in Prozent
 */
public record CompanyFigures(PolityView company, Listing listing, long price, long previousClose, long treasury,
                             long bookValue, long marketCap, long dividends30, double momentum) {

    public CompanyFigures {
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(listing, "listing");
    }

    public static CompanyFigures of(PolityView company, Listing listing, StockExchangeView exchange, long treasury,
                                    Day today) {
        long price = exchange.price(company.id());
        List<Quote> quotes = exchange.quotes(company.id());
        long previous = quotes.stream().filter(quote -> quote.day().isBefore(today)).reduce((a, b) -> b)
                .map(Quote::close).orElse(listing.initialPrice());
        long dividends = exchange.dividends(company.id()).stream()
                .filter(dividend -> dividend.day().plus(30).isAfter(today))
                .mapToLong(Dividend::perShare).sum();
        long older = quotes.size() > 5 ? quotes.get(quotes.size() - 6).close()
                : quotes.isEmpty() ? price : quotes.getFirst().open();
        double momentum = older == 0 ? 0 : (price - older) * 100.0 / older;
        return new CompanyFigures(company, listing, price, previous, treasury,
                treasury / Math.max(1, listing.totalShares()), price * listing.totalShares(), dividends, momentum);
    }

    /** Änderung zum Vortag in Prozent. */
    public double changePercent() {
        return previousClose == 0 ? 0 : (price - previousClose) * 100.0 / previousClose;
    }

    /** Dividendenrendite der letzten 30 Tage in Prozent. */
    public double dividendYield() {
        return price == 0 ? 0 : dividends30 * 100.0 / price;
    }
}
